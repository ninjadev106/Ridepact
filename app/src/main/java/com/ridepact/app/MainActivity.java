package com.ridepact.app;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import org.w3c.dom.Text;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    // The S3 client used for getting the list of objects in the bucket
    private AmazonS3Client s3;
    private ArrayList<HashMap<String, Object>> transferRecordMaps;
    private Util util;
    // This is the main class for interacting with the Transfer Manager
    private TransferUtility transferUtility;
    private int totalHtmlFilesCount = 0;
    private int downloadedHtmlFilesCount = 0;
    private int failedHtmlFilesCount = 0;

    private int totalVideoFilesCount = 0;
    private int downloadedVideoFilesCount = 0;
    private int failedVideoFilesCount = 0;
    private ArrayList<File> downloadedHtmlFiles = new ArrayList<>();
    private ArrayList<File> downloadedVideoFiles = new ArrayList<>();
    private ProgressDialog dialogProgress;

    private ArrayList<Integer> ids = new ArrayList<>();
    private ArrayList<Long> bytesCurrents = new ArrayList<>();
    private long totalSize = 0;
    private final Handler handler = new Handler();

    private final int MY_PERMISSIONS_REQUEST_ACCESS_EXTERNAL_STORAGE = 1000;
    private Boolean isRunning = false;
    //private Date newestDate;
    private ArrayList<Date> playedDates = new ArrayList<>();

    //private String newestDateString = "";
    private final String STORE_FILE_NAME = "STORE_FILE_NAME";
    private final String KEY_NEWEST_DATE = "NEWEST_DATE";
    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyy hh:mm:ss zz");
    //private Timer timer = null;
    //private int timeCount = 0;
    SharedPreferences sharedPreferences = null;
    SharedPreferences.Editor editor = null;
    private TextView textView;

    private int scheduleperiod = 60000; // 60 secs

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Read last play date from pref
//        sharedPreferences = getSharedPreferences(STORE_FILE_NAME, Context.MODE_PRIVATE);
//        editor = sharedPreferences.edit();
//
//        newestDateString = sharedPreferences.getString(KEY_NEWEST_DATE, "10 Oct 1978 0:00:00 GMT");
//        try {
//            newestDate = dateFormat.parse(newestDateString);
//        }
//        catch (Exception e){
//            Calendar calendar = Calendar.getInstance();
//            calendar.add(Calendar.YEAR , -10 );
//            newestDate = calendar.getTime();
//        }

        // Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        util = new Util();
        transferUtility = util.getTransferUtility(this);

         textView = findViewById(R.id.textview_time);

        initData();



    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!(ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) ||
                !(ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED))
        {
            // Show rationale and request permission.
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    MY_PERMISSIONS_REQUEST_ACCESS_EXTERNAL_STORAGE);

        }
        else {
            // Refresh the file list.
            if (!isRunning) {
                isRunning = true;
                new GetFileListTask().execute();
            }
        }

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == MY_PERMISSIONS_REQUEST_ACCESS_EXTERNAL_STORAGE) {

            if (resultCode == Activity.RESULT_OK) {
                // Refresh the file list.
                new GetFileListTask().execute();
            } else if (resultCode == Activity.RESULT_CANCELED) {
                // some stuff that will happen if there's no result
            }
        }
    }

    private void initData() {
        // Gets the default S3 client.
        s3 = util.getS3Client(MainActivity.this);
        transferRecordMaps = new ArrayList<HashMap<String, Object>>();
    }

    private void processSchedules(){
        totalHtmlFilesCount = 0;
        downloadedHtmlFilesCount = 0;
        failedHtmlFilesCount = 0;
        totalVideoFilesCount = 0;
        downloadedVideoFilesCount = 0;
        failedVideoFilesCount = 0;

        dialogProgress = ProgressDialog.show(MainActivity.this,
                getString(R.string.downloading_html),
                getString(R.string.please_wait));

        for(HashMap<String, Object> record : transferRecordMaps){
            String fileName = record.get("key").toString();
            if (fileName.endsWith(".html")){
                totalHtmlFilesCount ++;
                // Get html content
                downloadHtmlFile(fileName);

            }
        }

        // No html, continue schedule
        if (totalHtmlFilesCount == 0){
            dialogProgress.dismiss();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    textView.setText("Checking for new schedule...");
                    new GetFileListTask().execute();
                    //stopTimer();
                }
            }, scheduleperiod);

            textView.setText("No a new schedule to display");
            //startTimer();
        }
    }

    private void downloadHtmlFile(String htmlName){

        final File file = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) + "/" + htmlName);

        transferUtility.download(Constants.BUCKET_NAME, htmlName, file, new TransferListener() {
            @Override
            public void onStateChanged(int id, TransferState state) {
                if (state == TransferState.COMPLETED){
                    downloadedHtmlFilesCount ++;
                    downloadedHtmlFiles.add(file);
                    if (downloadedHtmlFilesCount + failedHtmlFilesCount == totalHtmlFilesCount){
                        downloadVideoFiles();
                    }
                }
                else if (state == TransferState.FAILED){
                    failedHtmlFilesCount ++;
                    if (downloadedHtmlFilesCount + failedHtmlFilesCount == totalHtmlFilesCount){
                        downloadVideoFiles();
                    }
                }
            }

            @Override
            public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {

            }

            @Override
            public void onError(int id, Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    private void downloadVideoFiles(){

        Boolean downloadingVideo = false;



        File newestFile = null;
        String newestHtmlContent = null;

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR , -10 );
        Date newestDate = calendar.getTime();


        for (final File file: downloadedHtmlFiles){
            if (file.exists()){
                //Read text from file
                StringBuilder text = new StringBuilder();

                try {
                    BufferedReader br = new BufferedReader(new FileReader(file));
                    String line;

                    while ((line = br.readLine()) != null) {
                        text.append(line);
                        text.append('\n');
                    }
                    br.close();

                    String htmlContent = text.toString();
                    // Searching for "expires" tag
                    final String keyStart = "<meta http-equiv=\"expires\" content=\"";
                    final String keyStartDate = "<meta http-equiv=\"date\" content=\"";


                    final String keyEnd = "\">";
                    int indexStart = htmlContent.indexOf(keyStart);
                    if (indexStart >= 0){
                        int indexEnd = htmlContent.indexOf(keyEnd, indexStart + keyStart.length());
                        if (indexEnd > 0){
                            String expires = htmlContent.substring(indexStart + keyStart.length(), indexEnd);
                            if (!isDateBefore(expires)){
                                indexStart = htmlContent.indexOf(keyStartDate);
                                if (indexStart >= 0){
                                    indexEnd = htmlContent.indexOf(keyEnd, indexStart + keyStartDate.length());
                                    if (indexEnd > 0){
                                        String dateStr = htmlContent.substring(indexStart + keyStartDate.length(), indexEnd);

                                        // Check start date is been reached or not
                                        if (isDateBefore(dateStr)){
                                            try{
                                                SimpleDateFormat dateFmt = new SimpleDateFormat("dd MMM yyy hh:mm:ss zz");
                                                Date date = dateFmt.parse(dateStr);

                                                if (!playedDates.contains(date)) {
                                                    if (date.compareTo(newestDate) >= 0) {
                                                        // Newer than played
                                                        newestFile = file;
                                                        newestHtmlContent = htmlContent;
                                                        newestDate = date;
                                                        //newestDateString = dateStr;
                                                    }
                                                }

                                            }
                                            catch (Exception e){

                                            }
                                        }




                                    }
                                }

                            }
                        }
                    }

                }
                catch (IOException e) {
                    //You'll need to add proper error handling here
                }

            }
        }

        if (newestFile != null) {

            playedDates.add(newestDate);

            downloadingVideo = true;
            downloadVideoFilesInHtml(newestHtmlContent, newestFile);
        }
        else {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    textView.setText("Checking for new schedule...");
                    new GetFileListTask().execute();
                    //stopTimer();
                }
            }, scheduleperiod);

            textView.setText("No a new schedule to display");
            //startTimer();
        }

        if (!downloadingVideo){
            dialogProgress.dismiss();
        }
    }

    private void downloadVideoFilesInHtml(final String htmlContent, final File htmlFile){
        totalVideoFilesCount = 0;
        downloadedVideoFilesCount = 0;
        failedVideoFilesCount = 0;

        dialogProgress.setTitle(getString(R.string.downloading_video));

        try{
            //<source src="filename1.mp4" type="video/mp4">
            //<source src="filename2.mp4" type="video/mp4">

            final ArrayList<String> videos = new ArrayList<>();

            final String keyStart = "<source src=\"";
            final String keyEnd = "\"";

            int indexStart = 0;
            int indexEnd = 0;

            while ((indexStart >= 0) && (indexEnd >= 0)){
                indexStart = htmlContent.indexOf(keyStart, indexEnd);
                if (indexStart >= 0){
                    indexEnd = htmlContent.indexOf(keyEnd, indexStart + keyStart.length());
                    if (indexEnd > 0){
                        String link = htmlContent.substring(indexStart + keyStart.length(), indexEnd);
                        videos.add(link);
                        totalVideoFilesCount += 1;
                    }
                }
            }

            // Download video file
            ids.clear();
            bytesCurrents.clear();
            totalSize = 0;

            if (videos.size() == 0){
                // No video, continue schedule
                if (totalHtmlFilesCount == 0){
                    dialogProgress.dismiss();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            textView.setText("Checking for new schedule...");
                            new GetFileListTask().execute();
                            //stopTimer();
                        }
                    }, scheduleperiod);

                    //startTimer();
                    textView.setText("No a new schedule to display");
                }
            }
            else {
                for (String video : videos) {
                    final File file = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) + "/" + video);

                    transferUtility.download(Constants.BUCKET_NAME, video, file, new TransferListener() {
                        @Override
                        public void onStateChanged(int id, TransferState state) {
                            if (state == TransferState.COMPLETED) {
                                downloadedVideoFilesCount++;
                                if (downloadedVideoFilesCount + failedVideoFilesCount == totalVideoFilesCount) {
                                    File firstFile = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) + "/" + videos.get(0));
                                    // Get video file to modify html content to fit
                                    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                                    retriever.setDataSource(firstFile.getPath());
                                    int width = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                                    int height = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                                    retriever.release();

                                    playHtmlWithVideos(htmlFile, htmlContent, width, height);
                                }
                            } else if (state == TransferState.FAILED) {
                                failedVideoFilesCount++;
                                if (downloadedVideoFilesCount + failedVideoFilesCount == totalVideoFilesCount) {

                                    File firstFile = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) + "/" + videos.get(0));
                                    // Get video file to modify html content to fit
                                    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                                    retriever.setDataSource(firstFile.getPath());
                                    int width = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                                    int height = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                                    retriever.release();

                                    playHtmlWithVideos(htmlFile, htmlContent, width, height);
                                }
                            }
                        }

                        @Override
                        public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                            if ((bytesTotal > 0) && !ids.contains(id)) {
                                ids.add(id);
                                bytesCurrents.add(0l);
                                totalSize += bytesTotal;
                            }

                            if (ids.size() == videos.size()) {
                                int index = ids.indexOf(id);
                                if (index >= 0) {
                                    bytesCurrents.remove(index);
                                    bytesCurrents.add(index, bytesCurrent);

                                    updateDownloadProgress();
                                }
                            }

                            Log.i("TAG", "######### onProgressChanged: bytesCurrent: " + bytesCurrent + " bytesTotal: " + bytesTotal);
                        }

                        @Override
                        public void onError(int id, Exception ex) {
                            ex.printStackTrace();
                        }
                    });
                }
            }

        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    private void playHtmlWithVideos(File html, String htmlContent, int width, int height){

        textView.setText("Play videos on the new schedule...");

        dialogProgress.dismiss();

        // Save newestDate to prefs

        // Don't save to pref
        //editor.putString(KEY_NEWEST_DATE, newestDateString);
        //editor.commit();

        // Modify html content based on video size
        float videoScale = (float)width/height;

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int screenheight = displayMetrics.heightPixels;
        int screenwidth = displayMetrics.widthPixels;

        float deviceScale = (float)screenwidth/screenheight;
        String leftStr = "0";
        String topStr = "0";
        String widthStr = "100%";
        String heightStr = "100%";

        if (deviceScale > videoScale){
            // Calculate percent
            topStr = "0";
            heightStr = "100%";

            leftStr = "" + (100 - (screenheight*videoScale*100)/screenwidth)/2 + "%";
            widthStr = "" + (screenheight*videoScale*100)/screenwidth + "%";

            /*
            int startPos = htmlContent.indexOf("<style>");
            int endPos;
            if (startPos >= 0){
                endPos = htmlContent.indexOf("</style>", startPos);
                if (endPos > startPos){
                    int startPosVideo = htmlContent.indexOf("video", startPos);
                    if (startPosVideo > startPos){
                        int startPosWidth = htmlContent.indexOf("width", startPosVideo);
                        if (startPosWidth > startPosVideo){
                            int startPos100Percent = htmlContent.indexOf("100%", startPosWidth);
                            if ((startPos100Percent > startPosWidth) && (startPos100Percent < endPos)){
                                StringBuffer sb = new StringBuffer(htmlContent);
                                sb.replace(startPos100Percent, startPos100Percent + 4, strPercent);
                                htmlContent = sb.toString();

                                try {
                                    OutputStreamWriter outputStreamWriter = new OutputStreamWriter(new FileOutputStream(html));
                                    outputStreamWriter.write(htmlContent);
                                    outputStreamWriter.close();
                                }
                                catch (Exception e) {
                                    Log.e("Exception", "File write failed: " + e.toString());
                                }

                            }
                        }
                    }
                }
            }
            */
        }
        else if (deviceScale < videoScale) {
            // Calculate percent
            leftStr = "0";
            widthStr = "100%";

            topStr = "" + (100 - (screenwidth/videoScale*100)/screenheight)/2 + "%";
            heightStr = "" + (screenwidth/videoScale*100)/screenheight + "%";

        }
        else{
        }

        htmlContent = htmlContent.replace("VIDEO_TOP", topStr);
        htmlContent = htmlContent.replace("VIDEO_LEFT", leftStr);
        htmlContent = htmlContent.replace("VIDEO_WIDTH", widthStr);
        htmlContent = htmlContent.replace("VIDEO_HEIGHT", heightStr);

        // Write back to file
        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(new FileOutputStream(html));
            outputStreamWriter.write(htmlContent);
            outputStreamWriter.close();
        }
        catch (Exception e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }

        Intent myIntent = new Intent(this, VideoPlayerActivity.class);
        myIntent.putExtra("video", html.getAbsolutePath());
        startActivity(myIntent);

        isRunning = false;
    }

    private Boolean isDateBefore(String dateStr){
        // "16 Nov 2018 14:30:00 GMT"
        try {
            SimpleDateFormat dateFmt = new SimpleDateFormat("dd MMM yyy hh:mm:ss zz");
            Date date = dateFmt.parse(dateStr);
            Date current = new Date();
            if (date.compareTo(current) < 0){
                return true;
            }
        }
        catch (Exception e){

        }

        return false;
    }

    private void updateDownloadProgress(){
        long bytesCurrent = 0;
        for (long curr : bytesCurrents){
            bytesCurrent += curr;
        }

        String strProgress = bytesSizeFromValue(bytesCurrent);
        String strTotal = bytesSizeFromValue(totalSize);
        String status = String.format("%s / %s downloaded...", strProgress, strTotal);

        dialogProgress.setMessage(status);
    }

    private static final DecimalFormat format = new DecimalFormat("#.##");
    private static final long MiB = 1024 * 1024;
    private static final long KiB = 1024;

    private String bytesSizeFromValue(long length){
        if (length > MiB) {
            return format.format(length / MiB) + " MiB";
        }
        if (length > KiB) {
            return format.format(length / KiB) + " KiB";
        }
        return format.format(length) + " B";
    }

    /*
    private void startTimer(){
        if (timer != null){
            timer.cancel();
            timer = null;
        }

        timeCount = 0;

        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        int mins = (300 - timeCount)/60;
                        int secs = 300 - timeCount - mins*60;

                        timeCount ++;

                        TextView textView = findViewById(R.id.textview_time);
                        textView.setText(String.format("%02d:%02d", mins, secs));

                    }
                });
            }
        }, 0, 1000);
    }

    private void stopTimer(){
        if (timer != null){
            timer.cancel();
            timer = null;
        }
    }
    */

    /**
     * This async task queries S3 for all files in the given bucket so that they
     * can be displayed on the screen
     */
    private class GetFileListTask extends AsyncTask<Void, Void, Void> {
        // The list of objects we find in the S3 bucket
        private List<S3ObjectSummary> s3ObjList;
        // A dialog to let the user know we are retrieving the files
        private ProgressDialog dialog;

        @Override
        protected void onPreExecute() {
            dialog = ProgressDialog.show(MainActivity.this,
                    getString(R.string.refreshing),
                    getString(R.string.please_wait));
        }

        @Override
        protected Void doInBackground(Void... inputs) {
            // Queries files in the bucket from S3.
            s3ObjList = s3.listObjects(Constants.BUCKET_NAME).getObjectSummaries();
            transferRecordMaps.clear();
            for (S3ObjectSummary summary : s3ObjList) {
                HashMap<String, Object> map = new HashMap<String, Object>();
                map.put("key", summary.getKey());
                transferRecordMaps.add(map);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            dialog.dismiss();
            processSchedules();
        }
    }


}
