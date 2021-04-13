package com.wzt.yolov5;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;

public class FileOperation {

    public static void initData(ArrayList<String> contents) {
        String filePath = Environment.getExternalStorageDirectory()+"/DCIM/log/";
        String fileName = System.currentTimeMillis()+".txt";
        writeTxtToFile(contents , filePath, fileName);
    }

    // 将字符串写入到文本文件中
    public static void writeTxtToFile(ArrayList<String> contents, String filePath, String fileName) {
        makeFilePath(filePath, fileName);
        String strFilePath = filePath+fileName;
        try {
            File file = new File(strFilePath);
            if (!file.exists()) {
                Log.d( "FileOperation" , "创建新文件" + strFilePath);
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
            RandomAccessFile raf = new RandomAccessFile(file, "rwd" );
            raf.seek(file.length());
            for (int i = 0; i < contents.size(); i++) {
                String strContent = contents.get(i) + "\r\n" ;
                raf.write(strContent.getBytes());
            }
            raf.close();
        } catch (Exception e) {
            Log.e( "FileOperation" , "Error on write File:" + e);
        }
    }

    // 生成文件
    public static File makeFilePath(String filePath, String fileName) {
        File file = null ;
        makeRootDirectory(filePath);
        try {
            file = new File(filePath + fileName);
            if (!file.exists()) {
                file.createNewFile();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return file;
    }

    // 生成文件夹
    public static void makeRootDirectory(String filePath) {
        File file = null ;
        try {
            file = new File(filePath);
            if (!file.exists()) {
                file.mkdir();
            }
        } catch (Exception e) {
            Log.i( "error:" , e+ "" );
        }
    }

}
