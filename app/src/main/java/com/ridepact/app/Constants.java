package com.ridepact.app;

public class Constants {

    /*
     * You should replace these values with your own. See the README for details
     * on what to fill in.
     */
    public static final String COGNITO_POOL_ID = "us-east-1:f9a7f6a4-4a99-4345-a546-10dfb73096b5";

    /*
     * Region of your Cognito identity pool ID.
     */
    public static final String COGNITO_POOL_REGION = "us-east-1";

    /*
     * Note, you must first create a bucket using the S3 console before running
     * the sample (https://console.aws.amazon.com/s3/). After creating a bucket,
     * put it's name in the field below.
     */
    public static final String BUCKET_NAME = "thanglv-s3-bucket";

    /*
     * Region of your bucket.
     */
    public static final String BUCKET_REGION = "us-east-2";
}
