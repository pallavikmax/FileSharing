package com.hutchgroup.elog.filesharing;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.io.CopyStreamAdapter;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.net.URLConnection;
import java.util.Date;

import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

public class MainActivity extends AppCompatActivity {

    public final static double GB = (1024 * 1024 * 1024);
    public final static double MB = (1024 * 1024);
    public final static double KB = (1024);

    Button btnCopy, btnConnect;
    ProgressBar progressBarCurrent, progressBar;
    TextView tvCurrentFileName, tvcurrentPercentage, tvCurrentProgress, tvTotalPercentage, tvTotalFiles, tvTotalSize, tvTimeElapsed;
    LinearLayout layoutProgress;

    private void initialize() {

        layoutProgress = (LinearLayout) findViewById(R.id.layoutProgress);
        // layoutProgress.setVisibility(View.GONE);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        progressBarCurrent = (ProgressBar) findViewById(R.id.progressBarCurrent);

        tvCurrentFileName = (TextView) findViewById(R.id.tvCurrentFileName);
        tvcurrentPercentage = (TextView) findViewById(R.id.tvcurrentPercentage);
        tvCurrentProgress = (TextView) findViewById(R.id.tvCurrentProgress);
        tvTotalPercentage = (TextView) findViewById(R.id.tvTotalPercentage);
        tvTotalFiles = (TextView) findViewById(R.id.tvTotalFiles);
        tvTotalSize = (TextView) findViewById(R.id.tvTotalSize);
        tvTimeElapsed = (TextView) findViewById(R.id.tvTimeElapsed);

        btnCopy = (Button) findViewById(R.id.btnCopy);
        btnCopy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                ConnectivityManager manager = (ConnectivityManager) getApplicationContext().getSystemService(MainActivity.CONNECTIVITY_SERVICE);
                Boolean isWifi = manager.getNetworkInfo(
                        ConnectivityManager.TYPE_WIFI).isConnectedOrConnecting();
                if (!isWifi) {
                    Toast.makeText(MainActivity.this, "Please connect to wifi to proceed!", Toast.LENGTH_LONG).show();
                    return;
                }

                new FTPDownload().execute();
                //new CopyData().execute();
                //  final DownloadTask downloadTask = new DownloadTask();
                // downloadTask.execute("http://192.168.1.1/USB_Storage/SygicNew.zip");
            }
        });

        btnConnect = (Button) findViewById(R.id.btnConnect);
        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(Settings.ACTION_WIFI_SETTINGS), 0);
            }
        });
    }

    Thread thTimer = null;
    long timeElapsed = 0;

    void updateTime() {
        if (thTimer != null) {
            timeElapsed = 0;
            thTimer.interrupt();
            thTimer = null;
        }

        thTimer = new Thread() {

            @Override
            public void run() {
                try {
                    while (!isInterrupted()) {
                        Thread.sleep(1000);
                        timeElapsed++;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                String time = formatSeconds(timeElapsed);
                                tvTimeElapsed.setText("Time Elapsed: " + time);

                                // update TextView here!
                            }
                        });
                    }
                } catch (InterruptedException e) {
                }
            }
        };

        thTimer.start();
    }

    // convert seconds to hour minute seconds
    String formatSeconds(long second) {
        String time = "";
        long hours = second / 3600;
        long minute = (second % 3600) / 60;
        long sec = (second % 3600) % 60;
        if (hours > 0) {
            time = hours + " hours ";
        }

        if (minute > 0) {
            time += minute + " minutes ";
        }

        if (sec > 0) {
            time += sec + " seconds";
        }
        return time;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initialize();
        checkAndGrantPermissions();
    }

    public boolean hasPermissions(String... permissions) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && getApplicationContext() != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }

        return true;
    }

    public void checkAndGrantPermissions() {

        String[] PERMISSIONS = {Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.INTERNET, android.Manifest.permission.WRITE_EXTERNAL_STORAGE, android.Manifest.permission.READ_EXTERNAL_STORAGE
                , Manifest.permission.ACCESS_NETWORK_STATE
        };
        if (!hasPermissions(PERMISSIONS)) {
            ActivityCompat.requestPermissions(MainActivity.this, PERMISSIONS, 999);
        }
    }

    private String formatSize(double size) {
        String tSize = "";
        if (size >= GB) {
            tSize = String.format("%.2f", (size / GB)) + "GB";
        } else if (size >= MB) {
            tSize = String.format("%.2f", (size / MB)) + "MB";
        } else if (size >= KB) {
            tSize = String.format("%.2f", (size / KB)) + "KB";
        } else {

            tSize = size + "Bytes";
        }
        return tSize;
    }

    public class CopyData extends AsyncTask<Void, Double, Boolean> {

        int count = 0, copyCount = 0;
        double size = 0, copySize = 0, currentFileSize = 0d;
        String currentFileName = "", error;

        public CopyData() {

        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            layoutProgress.setVisibility(View.VISIBLE);
            progressBar.setProgress(0);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (result) {
                Toast.makeText(MainActivity.this, "Files are copied successfully", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(MainActivity.this, "Failed to copy all files, Error: " + error, Toast.LENGTH_LONG).show();

            }
        }

        @Override
        protected void onProgressUpdate(Double... values) {
            super.onProgressUpdate(values);
            try {
                if (values.length == 0) {
                    updateTotalProgress();
                } else {

                    updateCurrentProgress(values[0]);
                }
            } catch (Exception exe) {
            }
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            boolean status = true;
            try {

                Authenticator.setDefault(new Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication("admin", "#72Hutch5".toCharArray());
                    }
                });
                String serverPath = "smb://192.168.1.1//USB_Storage//Navigation//";
                serverPath = "smb://readyshare.routerlogin.net//USB_Storage//Navigation//";
                SmbFile source = new SmbFile(serverPath);
                String path = Environment.getExternalStorageDirectory() + "//NavigationFTP";
                //NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication("192.168.0.1", "admin", "85Jaswal");
                getFile(source);
                status = copyDirectory(source, new File(path));
            } catch (Exception exe) {
                error = exe.getMessage();
                exe.printStackTrace();
                status = false;
            }
            if (status) {
                status = finalizeProcess();
            }
            return status;
        }


        public boolean copy(SmbFile sourceLocation, File targetLocation) throws IOException {
            boolean result = true;
            if (sourceLocation.isDirectory()) {
                result = copyDirectory(sourceLocation, targetLocation);
            } else {
                String httpPath = sourceLocation.getCanonicalPath().replace("smb://", "http://");
                String ftpPath = httpPath.replace("http://readyshare.routerlogin.net", "/shares");
                currentFileSize = sourceLocation.getContentLength();
                currentFileName = ftpPath;// httpPath;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressBarCurrent.setProgress(0);

                        tvCurrentFileName.setText("Current File: " + currentFileName);
                    }
                });
                //copyFile(sourceLocation.getInputStream(), targetLocation);
                // convert smb path to http path

                //   httpPath = "http://192.168.1.1/USB_Storage/SygicNew.zip";
                // result = downloadFile(httpPath, targetLocation);
                result = downloadFileFromFTP(ftpPath, targetLocation);
                if (result) {
                    copyCount++;
                    copySize += currentFileSize;
                    publishProgress();
                }

            }
            return result;
        }

        private boolean copyDirectory(SmbFile source, File target) throws IOException {
            boolean status = true;
            if (!target.exists()) {
                target.mkdir();
            }

            for (SmbFile f : source.listFiles()) {
                status = copy(f, new File(target, f.getName()));
                if (!status)
                    break;
            }
            return status;
        }

        private void copyFile(InputStream in, File target) throws IOException {

            OutputStream out = new FileOutputStream(target);

            byte[] buf = new byte[1024];
            int length;
            double read = 0;
            while ((length = in.read(buf)) > 0) {
                read += length;
                out.write(buf, 0, length);
                publishProgress(read);

            }
            out.close();
        }

        public void getFile(SmbFile source) throws SmbException {
            SmbFile[] files = source.listFiles();

            if (files != null)
                for (int i = 0; i < files.length; i++) {
                    SmbFile file = files[i];

                    if (file.isDirectory()) {
                        getFile(file);
                    } else {
                        size += file.getContentLength();
                        count++;

                    }
                }
        }

        private void updateTotalProgress() {
            int progress = (int) (copySize * 100 / size);
            progressBar.setProgress(progress);
            String totalFiles = String.format("%s of %s Files", copyCount, count);
            tvTotalFiles.setText(totalFiles);

            String totalSize = String.format("%s of %s", formatSize(copySize), formatSize(size));
            tvTotalSize.setText(totalSize);
            tvTotalPercentage.setText(progressBar.getProgress() + "%");
        }

        private void updateCurrentProgress(double current) {

            int progress = (int) (current * 100 / currentFileSize);
            progressBarCurrent.setProgress(progress);
            String currentSize = String.format("%s of %s", formatSize(current), formatSize(currentFileSize));
            tvCurrentProgress.setText(currentSize);
            tvcurrentPercentage.setText(progressBarCurrent.getProgress() + "%");
        }

        private boolean downloadFile(String fromUrl, File target) {
            InputStream input = null;
            OutputStream output = null;
            HttpURLConnection connection = null;
            boolean status = true;
            try {
                URL url = new URL(fromUrl);


                connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                // expect HTTP 200 OK, so we don't mistakenly save error report
                // instead of the file
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    error = "Server returned HTTP " + connection.getResponseCode()
                            + " " + connection.getResponseMessage();
                    status = false;
                    return status;
                }

                // this will be useful to display download percentage
                // might be -1: server did not report the length
                int fileLength = connection.getContentLength();
                currentFileSize = fileLength;
                // download the file
                input = connection.getInputStream();
                output = new FileOutputStream(target);

                byte data[] = new byte[4096];
                double total = 0d;
                int count;
                while ((count = input.read(data)) != -1) {
                    // allow canceling with back button
                    if (isCancelled()) {
                        input.close();
                        return false;
                    }
                    total += count;
                    copySize = total;
                    // publishing the progress....
                    if (fileLength > 0) // only if total length is known
                        publishProgress((double) (total * 100 / fileLength));
                    output.write(data, 0, count);
                }
            } catch (Exception e) {
                status = false;
                error = e.toString();
            } finally {
                try {
                    if (output != null)
                        output.close();
                    if (input != null)
                        input.close();
                } catch (IOException ignored) {
                }

                if (connection != null)
                    connection.disconnect();
            }
            return status;
        }

        private boolean finalizeProcess() {
            try {
                File file = new File("Navigation");
                File file2 = new File("NavigationOld");
                boolean success = file.renameTo(file2);
                if (success) {
                    file = new File("NavigationNew");
                    file2 = new File("Navigation");
                    success = file.renameTo(file2);
                    if (success) {
                        file2 = new File("NavigationOld");
                        file2.delete();
                    }

                }
                return true;
            } catch (Exception exe) {
                error = exe.getMessage();
                exe.printStackTrace();
                return false;
            }
        }

        CopyStreamAdapter streamListener;

        private boolean downloadFileFromFTP(String source, File target) {
            InputStream input = null;
            OutputStream output = null;
            FTPClient ftpClient = new FTPClient();
            boolean status = true;
            try {
                ftpClient.connect("192.168.1.1", 21);
                ftpClient.enterLocalPassiveMode();
                // ftpClient.login("admin", "#72Such5");

                ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE);// Used for video

                // this will be useful to display download percentage
                // might be -1: server did not report the length
                // download the file
                input = ftpClient.retrieveFileStream(source);
                output = new FileOutputStream(target);

                int fileLength = input.available();
                currentFileSize = fileLength;
                byte data[] = new byte[4096];
                double total = 0d;
                int count;
                while ((count = input.read(data)) != -1) {
                    // allow canceling with back button
                    if (isCancelled()) {
                        input.close();
                        return false;
                    }
                    total += count;
                    copySize = total;
                    // publishing the progress....
                    if (fileLength > 0) // only if total length is known
                        publishProgress((double) (total * 100 / fileLength));
                    output.write(data, 0, count);
                }
            } catch (Exception e) {
                error = e.toString();
                status = false;
            } finally {
                try {
                    if (output != null)
                        output.close();
                    if (input != null)
                        input.close();
                    ftpClient.logout();
                    ftpClient.disconnect();
                } catch (IOException ignored) {
                }

            }
            return status;
        }

        private boolean downloadFtp(String sourcePath, File target) {
            boolean status = true;
            FileOutputStream videoOut;
            InputStream input = null;
            FTPClient ftpClient = new FTPClient();
            try {
                ftpClient.connect("192.168.1.1", 21);
                ftpClient.enterLocalPassiveMode();
                //    ftpClient.login("admin", "#72Such5");

                ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE);// Used for video

                //videoOut = new FileOutputStream(target);
                //boolean result = ftpClient.retrieveFile("/" + "filename-to-download", videoOut);

                BufferedInputStream buffIn = null;

                buffIn = new BufferedInputStream(ftpClient.retrieveFileStream(sourcePath), 8192);
                streamListener = new CopyStreamAdapter() {
                    @Override
                    public void bytesTransferred(long totalBytesTransferred,
                                                 int bytesTransferred, long streamSize) {
                        // this method will be called everytime some
                        // bytes are transferred
                        // System.out.println("Stream size" + file.length());
                        // System.out.println("byte transfeedd "
                        // + totalBytesTransferred);

                        int percent = (int) (totalBytesTransferred * 100 / streamSize);
                        publishProgress((double) percent);
                        //    pDialog.setProgress(percent);

                        if (totalBytesTransferred == streamSize) {
                            System.out.println("100% transfered");

                            removeCopyStreamListener(streamListener);

                        }

                    }
                };

                ftpClient.setCopyStreamListener(streamListener);

                status = ftpClient.storeFile(target.getAbsolutePath(), buffIn);
                System.out.println("Status Value-->" + status);
                buffIn.close();
                ftpClient.logout();
                ftpClient.disconnect();
                // videoOut.close();

            } catch (IOException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return status;
        }
    }

    // usually, subclasses of AsyncTask are declared inside the activity class.
// that way, you can easily modify the UI thread from here
    private class DownloadTask extends AsyncTask<String, Integer, String> {

        double size = 0, copySize = 0, currentFileSize = 0d;

        public DownloadTask() {
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            layoutProgress.setVisibility(View.VISIBLE);
            progressBar.setProgress(0);
            updateTime();
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            super.onProgressUpdate(progress);
            // if we get here, length is known, now set indeterminate to false
            progressBarCurrent.setIndeterminate(false);

            progressBarCurrent.setProgress(progress[0]);
            progressBarCurrent.setMax(100);
            tvcurrentPercentage.setText(progressBarCurrent.getProgress() + "%");
            String currentSize = String.format("%s of %s", formatSize(copySize), formatSize(currentFileSize));
            tvCurrentProgress.setText(currentSize);
        }

        @Override
        protected void onPostExecute(String result) {


            if (result != null)
                Toast.makeText(getApplicationContext(), "Download error: " + result, Toast.LENGTH_LONG).show();
            else
                Toast.makeText(getApplicationContext(), "File downloaded", Toast.LENGTH_SHORT).show();
        }

        @Override
        protected String doInBackground(String... sUrl) {
            InputStream input = null;
            OutputStream output = null;
            HttpURLConnection connection = null;
            try {
                URL url = new URL(sUrl[0]);

                Authenticator.setDefault(new Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication("Admin", "#72Hutch5".toCharArray());
                    }
                });
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                // expect HTTP 200 OK, so we don't mistakenly save error report
                // instead of the file
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    return "Server returned HTTP " + connection.getResponseCode()
                            + " " + connection.getResponseMessage();
                }

                // this will be useful to display download percentage
                // might be -1: server did not report the length
                int fileLength = connection.getContentLength();
                currentFileSize = fileLength;
                // download the file
                input = connection.getInputStream();
                String path = Environment.getExternalStorageDirectory() + "//Navigationhttp.exe";
               /* File file = new File(path);
                if (!file.exists()) {
                    file.createNewFile();
                }*/
                output = new FileOutputStream(path);

                byte data[] = new byte[4096];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    // allow canceling with back button
                    if (isCancelled()) {
                        input.close();
                        return null;
                    }
                    total += count;
                    copySize = total;
                    // publishing the progress....
                    if (fileLength > 0) // only if total length is known
                        publishProgress((int) (total * 100 / fileLength));
                    output.write(data, 0, count);
                }
            } catch (Exception e) {
                return e.toString();
            } finally {
                try {
                    if (output != null)
                        output.close();
                    if (input != null)
                        input.close();
                } catch (IOException ignored) {
                }

                if (connection != null)
                    connection.disconnect();
            }
            return null;
        }
    }

    public class FTPDownload extends AsyncTask<Void, Double, Boolean> {
        FTPClient ftpClient;
        int count = 0, copyCount = 0;
        double size = 0, copySize = 0, currentFileSize = 0d;
        String currentFileName = "", error;

        public FTPDownload() {

            ftpClient = new FTPClient();
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            layoutProgress.setVisibility(View.VISIBLE);
            progressBar.setProgress(0);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (result) {
                Toast.makeText(MainActivity.this, "Files are copied successfully", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(MainActivity.this, "Failed to copy all files, Error: " + error, Toast.LENGTH_LONG).show();

            }
        }

        @Override
        protected void onProgressUpdate(Double... values) {
            super.onProgressUpdate(values);
            try {
                if (values.length == 0) {
                    updateTotalProgress();
                } else {
                    updateCurrentProgress(values[0]);
                }
            } catch (Exception exe) {
            }
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            boolean status;
            try {


                String serverPath = "smb://192.168.1.1//USB_Storage//Navigation//";
                SmbFile source = new SmbFile(serverPath);
                String path = Environment.getExternalStorageDirectory() + "//NavigationFTP";
                getFile(source);

                ftpClient.connect("192.168.1.1");
                ftpClient.enterLocalPassiveMode();
                ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE);

                status = copyDirectory(source, new File(path));
            } catch (Exception exe) {
                error = exe.getMessage();
                exe.printStackTrace();
                status = false;
            } finally {
                try {
                    ftpClient.disconnect();
                    thTimer.interrupt();
                } catch (IOException e) {

                }
            }
            if (status) {
                status = finalizeProcess();
            }
            return status;
        }


        public boolean copy(SmbFile sourceLocation, File targetLocation) throws IOException {
            boolean result = true;
            if (sourceLocation.isDirectory()) {
                result = copyDirectory(sourceLocation, targetLocation);
            } else {
                String httpPath = sourceLocation.getCanonicalPath().replace("smb://", "http://");
                String ftpPath = httpPath.replace("http://192.168.1.1", "/shares");

                currentFileSize = sourceLocation.getContentLength();
                currentFileName = ftpPath;// httpPath;

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressBarCurrent.setProgress(0);
                        tvCurrentFileName.setText("Current File: " + currentFileName);
                    }
                });

                result = downloadFileFromFTP(ftpPath, targetLocation);
                if (result) {
                    copyCount++;
                    copySize += currentFileSize;
                    publishProgress();
                }

            }
            return result;
        }

        private boolean copyDirectory(SmbFile source, File target) throws IOException {
            boolean status = true;
            if (!target.exists()) {
                target.mkdir();
            }

            for (SmbFile f : source.listFiles()) {
                status = copy(f, new File(target, f.getName()));
                if (!status)
                    break;
            }
            return status;
        }

        public void getFile(SmbFile source) throws SmbException {
            SmbFile[] files = source.listFiles();

            if (files != null)
                for (int i = 0; i < files.length; i++) {
                    SmbFile file = files[i];

                    if (file.isDirectory()) {
                        getFile(file);
                    } else {
                        size += file.getContentLength();
                        count++;

                    }
                }
        }

        private void updateTotalProgress() {
            int progress = (int) (copySize * 100 / size);
            progressBar.setProgress(progress);
            String totalFiles = String.format("%s of %s Files", copyCount, count);
            tvTotalFiles.setText(totalFiles);

            String totalSize = String.format("%s of %s", formatSize(copySize), formatSize(size));
            tvTotalSize.setText(totalSize);
            tvTotalPercentage.setText(progressBar.getProgress() + "%");
        }

        private void updateCurrentProgress(double current) {

            int progress = (int) (current * 100 / currentFileSize);
            progressBarCurrent.setProgress(progress);
            String currentSize = String.format("%s of %s", formatSize(current), formatSize(currentFileSize));
            tvCurrentProgress.setText(currentSize);
            tvcurrentPercentage.setText(progressBarCurrent.getProgress() + "%");
        }

        private boolean finalizeProcess() {
            try {
                File file = new File("Navigation");
                File file2 = new File("NavigationOld");
                boolean success = file.renameTo(file2);
                if (success) {
                    file = new File("NavigationNew");
                    file2 = new File("Navigation");
                    success = file.renameTo(file2);
                    if (success) {
                        file2 = new File("NavigationOld");
                        file2.delete();
                    }

                }
                return true;
            } catch (Exception exe) {
                error = exe.getMessage();
                exe.printStackTrace();
                return false;
            }
        }

        private boolean downloadFileFromFTP(String source, File target) {
            InputStream input = null;
            OutputStream output = null;
            boolean status = true;
            try {
                input = ftpClient.retrieveFileStream(source);
                if (input == null) {
                    error = "Input stream is null";
                    return false;
                }
                output = new FileOutputStream(target);

                byte data[] = new byte[4096];
                double total = 0d;
                int count;
                while ((count = input.read(data)) != -1) {
                    // allow canceling with back button
                    if (isCancelled()) {
                        input.close();
                        return false;
                    }
                    total += count;
                    publishProgress(total);
                    output.write(data, 0, count);
                }
            } catch (Exception e) {
                error = e.toString();
                status = false;
            } finally {
                try {
                    if (output != null)
                        output.close();
                    if (input != null)
                        input.close();
                } catch (IOException ignored) {
                }

            }
            return status;
        }

    }


}
