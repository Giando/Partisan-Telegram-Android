package org.telegram.messenger.partisan.appmigration;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

class MigrationReceiver {
    private final Activity activity;

    private MigrationReceiver(Activity activity) {
        this.activity = activity;
    }

    static void receiveZip(Activity activity) {
        new Thread(() -> new MigrationReceiver(activity).receiveZipInternal()).start();
    }

    private void receiveZipInternal() {
        try {
            AndroidUtilities.runOnUIThread(() -> {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.telegramDataReceived);
            });
            deleteSharedPrefs();
            ZipInputStream zipStream = createZipStream(activity.getIntent());
            unpackZip(zipStream);
            zipStream.close();
            //noinspection ResultOfMethodCallIgnored
            new File(activity.getFilesDir(), "updater_files_copied").createNewFile();
            finishReceivingMigration();
        } catch (Exception ex) {
            showMigrationReceiveError(ex);
        }
    }

    private void deleteSharedPrefs() {
        File prefsDir = new File(activity.getFilesDir().getParentFile(), "shared_prefs");
        if (prefsDir.exists()) {
            deleteFilesRecursive(prefsDir, false);
        }
    }

    private static void deleteFilesRecursive(@NonNull File fileOrDirectory, boolean deleteThis) {
        if (fileOrDirectory.isDirectory()) {
            File[] files = fileOrDirectory.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteFilesRecursive(child, true);
                }
            }
        }

        if (deleteThis) {
            //noinspection ResultOfMethodCallIgnored
            fileOrDirectory.delete();
        }
    }

    private @NonNull ZipInputStream createZipStream(Intent intent) throws FileNotFoundException, GeneralSecurityException {
        InputStream inputStream = createInputStream(intent);
        BufferedInputStream bufferedStream = new BufferedInputStream(inputStream);
        CipherInputStream cipherStream = new CipherInputStream(bufferedStream, createCipher());
        return new ZipInputStream(cipherStream);
    }

    private InputStream createInputStream(Intent intent) throws FileNotFoundException {
        InputStream inputStream;
        if (Build.VERSION.SDK_INT >= 24) {
            inputStream = activity.getContentResolver().openInputStream(intent.getData());
        } else {
            inputStream = new FileInputStream(intent.getData().getPath());
        }
        return inputStream;
    }

    private Cipher createCipher() throws GeneralSecurityException {
        byte[] passwordBytes = activity.getIntent().getByteArrayExtra("zipPassword");
        SecretKey key = new SecretKeySpec(passwordBytes, "AES");

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
        cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(passwordBytes));
        return cipher;
    }

    private void unpackZip(ZipInputStream zipStream) throws IOException {
        ZipEntry zipEntry = zipStream.getNextEntry();
        while (zipEntry != null) {
            File newFile = createFileFromZipEntry(activity.getFilesDir(), zipEntry);
            if (zipEntry.isDirectory()) {
                if (!newFile.isDirectory() && !newFile.mkdirs()) {
                    throw new IOException("Failed to create directory " + newFile);
                }
            } else {
                File parent = newFile.getParentFile();
                if (parent == null || !parent.isDirectory() && !parent.mkdirs()) {
                    throw new IOException("Failed to create directory " + parent);
                }

                writeFileContent(zipStream, newFile);
            }
            zipEntry = zipStream.getNextEntry();
        }
    }

    private static File createFileFromZipEntry(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());

        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destFile;
    }

    private static void writeFileContent(ZipInputStream zipStream, File newFile) throws IOException {
        byte[] buffer = new byte[8192];
        FileOutputStream fileOutputStream = new FileOutputStream(newFile);
        while (true) {
            int len = zipStream.read(buffer);
            if (len <= 0) {
                break;
            }
            fileOutputStream.write(buffer, 0, len);
        }
        fileOutputStream.close();
    }

    private void finishReceivingMigration() {
        AndroidUtilities.runOnUIThread(() -> {
            if (Build.VERSION.SDK_INT < 24) {
                File dataFile = new File(activity.getIntent().getData().getPath());
                File copiedFile = new File(dataFile.getParentFile(), "copied");
                try {
                    copiedFile.createNewFile();
                } catch (Exception ignored) {
                }
            }

            Intent data = new Intent();
            data.putExtra("copied", true);
            data.putExtra("packageName", activity.getPackageName());
            activity.setResult(Activity.RESULT_OK, data);

            activity.finish();
            android.os.Process.killProcess(android.os.Process.myPid());
        });
    }

    private void showMigrationReceiveError(Exception ex) {
        Log.e("BasePermissionActivity", "Error", ex);
        AndroidUtilities.runOnUIThread(() -> {
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.telegramDataReceivingError);
            Toast.makeText(activity, "Error: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        });
    }
}
