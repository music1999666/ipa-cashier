package com.ipaloma.jxbpay;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.widget.ImageView;

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

public class QrActivity extends AppCompatActivity {

    private static final String TAG = "QrActivity";
    private Context mActivity;
    private Intent mStartIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mActivity = this;
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        mStartIntent = getIntent();
        new Runnable(){
            @Override
            public void run() {
                String qrCode = mStartIntent.getStringExtra("qrcode");
                Log.d(TAG, "get qrcode : " + qrCode);

                ImageView web = findViewById(R.id.qrCodeImageView);
                if(qrCode == null || qrCode.equals(""))
                    qrCode = "www.baidu.com";
                Bitmap bitmap = QRCodeHelper
                        .newInstance(mActivity)
                        .setContent(qrCode)
                        .setErrorCorrectionLevel(ErrorCorrectionLevel.Q)
                        .setMargin(2)
                        .getQRCOde();
                web.setImageBitmap(bitmap);
            }
        }.run();

    }

}
