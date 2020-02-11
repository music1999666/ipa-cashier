package com.ipaloma.jxbpay;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class JXBCashierActivity extends AppCompatActivity
        implements GetPayRequestTask.GetPayRequestResponse,
        PaymentStatusTask.PaymentStatusResponse {

    private static final String TAG = "JXBCashierActivity";
    private Intent mStartIntent;

    private JSONObject parameter;
    private JSONObject mResultJson = new JSONObject();

    private String mResultUrl;
    private Activity mActivity;
    String qrCode = "";
    private TextView loadQr;
    private ImageView qrPay;
    private TextView cashTotal;
    private TextView customer;
    private TextView tickText;
    private AsyncTask<Void, Void, String> mPayRequestTask;
    private AsyncTask<Void, Void, String> mPaymentStatusTask;

    private int mQrDuration = 60 * 1000;
    private long qrTime = 0;
    private TextView payByCash;
    private TextView payByQr;

    private JSONObject mPayDescShown = new JSONObject();
    private int mCurrentMode = R.id.pay_qr;
    private String sessionId;
    private Button qrAction;
    private Button qrCancel;
    private Button qrMannualChecked;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mActivity = this;
        mStartIntent = getIntent();
        if (mStartIntent.getExtras() != null)
            Log.d(TAG, "start parameter" + mStartIntent.getExtras().toString());

        try {
            sessionId = mStartIntent.getStringExtra("sessionid");
            String para_str = mStartIntent.getStringExtra("parameter");
            if(sessionId != null  && !sessionId.equals("")&& !"".equals(JXBCashierParameter.input.optString(sessionId, "")))
            {
                para_str = JXBCashierParameter.input.optString(sessionId, "");
                JXBCashierParameter.input.remove(sessionId);
            }

            mResultJson.putOpt("input", new JSONObject(para_str));
            mResultJson.putOpt("output", new JSONObject(getString(R.string.cancel_result)));

            parameter = new JSONObject(para_str);
            parameter.remove("notifyurl");
            parameter.remove("returnurl");
            parameter.putOpt("amount", parameter.optDouble("count"));
            parameter.remove("count");
            parameter.putOpt("billnumber", parameter.optString("billid", ""));
            parameter.putOpt("method", "qrpay");
            parameter.putOpt("return_guid", true);
            parameter.putOpt("return_array", true);
            parameter.putOpt("pay_type", "c2b聚合支付");
            parameter.putOpt("no_redirect", true);
        } catch (JSONException e) {
            Log.e(TAG, "error parameters", e);
            e.printStackTrace();
            parameter = new JSONObject();
        }

        setContentView(R.layout.activity_jxbcashier);

        //显示收款场景
        String category = parameter.optString("scene", "");
        TextView title = (TextView) findViewById(R.id.toolbar_title);
        title.setText("经销宝收银台" + (category.equals("") ? "" : "-") + category);

        // 显示客户名称
        customer = (TextView) findViewById(R.id.customer_name);
        if (!parameter.optString("customername", "").equals(""))
            customer.setText(parameter.optString("customername"));

        // 显示金额
        cashTotal = (TextView) findViewById(R.id.cash_total);
        double amount = parameter.optDouble("amount", 0);
        String amount_text = parameter.optString("amount", "");

        if (amount > 0)
            cashTotal.setText(String.format("￥%,.2f元", amount));
        else if (!amount_text.equals(""))
            cashTotal.setText(String.format("￥%s元", amount_text));

        payByCash = (TextView) findViewById(R.id.pay_cash);
        payByQr = (TextView) findViewById(R.id.pay_qr);


        loadQr = (TextView) findViewById(R.id.load_qr_hint);
        qrPay = (ImageView) findViewById(R.id.qr);

        loadQr.setVisibility(View.VISIBLE);
        qrPay.setVisibility(View.INVISIBLE);

        qrCancel = (Button)findViewById(R.id.qr_cancel);
        qrMannualChecked = (Button)findViewById(R.id.qr_manual_checked);
        qrAction = (Button)findViewById(R.id.qr_action);
        loadQr.setClickable(false);

        tickText = (TextView) findViewById(R.id.tick_text);

        setClickListener();

        // 初始化为二维码支付
        payByQr.callOnClick();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }
    private void refresh(){
        // 公司收款功能介绍中，不起动加载二维码
        if(mCurrentMode == R.id.pay_qr && findViewById(R.id.pay_desc_layout).getVisibility() == View.VISIBLE){
            Log.d(TAG, "公司收款功能介绍中，不起动加载二维码");
            return;
        }

        // 正在获取二维码，不做任何动作
        if (mPayRequestTask != null && mPayRequestTask.getStatus() != AsyncTask.Status.FINISHED){
            Log.d(TAG, "正在获取二维码，不做任何动作");
            return;
        }

        // 支付成功
        if (qrTime < 0) {
            Log.d(TAG, "resumed, nothing to do");
            return;
        }
        // 获取二维码任务没有运行并且当前没有展示二维码，或者二维码10秒以内到期
        if ((mPayRequestTask == null || mPayRequestTask.getStatus() == AsyncTask.Status.FINISHED)
                && (qrPay.getVisibility() == View.INVISIBLE || (System.currentTimeMillis() - qrTime) > mQrDuration - 10 * 1000)) {
            Log.d(TAG, "resumed, to start new pay request");
            CancelTasks();
            mPayRequestTask = new GetPayRequestTask(parameter, mActivity, JXBCashierActivity.this)
                    .execute();
            return;
        }

        // 如果二维码可见，可用，启动状态查询，限定时间为剩余时间
        Log.d(TAG, "resumed, to start new payment status check " + (mQrDuration - System.currentTimeMillis() + qrTime) + " millis");
        if (mPaymentStatusTask != null && mPaymentStatusTask.getStatus() != AsyncTask.Status.FINISHED)
            mPaymentStatusTask.cancel(true);
        mPaymentStatusTask = new PaymentStatusTask(mResultUrl, mQrDuration - System.currentTimeMillis() + qrTime, this, this)
                .execute();

    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "stop activity");
        CancelTasks();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // TODO callback with the result
        CancelTasks();
        try {
            // 取消状态
            //      获取二维码失败
            //      获取二维码成功但没有完成支付
            //      获取二维码成功，但查询状态失败
            // 成功状态
            //      完成支付
            Log.d(TAG, "\ndestroy activity, return with result\n" + mResultJson.toString(4));
            if(sessionId != null && !sessionId.equals(""))
            {
                JXBCashierParameter.output.put(sessionId, mResultJson.toString());
            }
            Log.d(TAG, "onActivityResult start close app");
            mStartIntent.putExtra("sessionid", sessionId);
            setResult(RESULT_OK, mStartIntent);

        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    private void CancelTasks() {
        if (mPaymentStatusTask != null && mPaymentStatusTask.getStatus() != AsyncTask.Status.FINISHED)
            mPaymentStatusTask.cancel(true);
        if (mPaymentStatusTask != null && mPaymentStatusTask.getStatus() != AsyncTask.Status.FINISHED)
            mPaymentStatusTask.cancel(true);
    }

    @Override
    public synchronized void onPayRequest(String result) {

        // Stop the previous payment status
        if (mPaymentStatusTask != null && mPaymentStatusTask.getStatus() != AsyncTask.Status.FINISHED)
            mPaymentStatusTask.cancel(true);
        tickText.setText("");
        tickText.setVisibility(View.INVISIBLE);

        Log.i(TAG, "onPayRequest-->" + result);
        int retCode = RESULT_CANCELED;
        String retMessage = "";
        JSONObject json = null;

        try {

            json = new JSONObject(result == null ? "{\"error\":\"网络开小差了\"}" : result);
            String error = json.optString("error", "").trim();
            mResultUrl = json.optString("result_url", "");  // 从服务器返回的状态查询 url

            qrCode = json.optString("qrcode", "");

            retCode = error.equals("") && !mResultUrl.equals("") && !qrCode.equals("") ? RESULT_OK : RESULT_CANCELED;
            retMessage = error;

            mStartIntent.putExtra("result_url", mResultUrl);

        } catch (JSONException e) {
            Log.e(TAG, "exception:", e);
            retCode = RESULT_CANCELED;
            retMessage = "网络开小差了";
            try {
                json = new JSONObject("{\"error\":\"网络开小差了\"}");
            } catch (JSONException e1) {
                e1.printStackTrace();
            }
        }
        // 已经支付成功
        if (json != null && json.optJSONArray("content") != null
                && json.optJSONArray("content").optJSONObject(0) != null
                && json.optJSONArray("content").optJSONObject(0).optString("sessionstatus", "").equals("成功")) {
            onPaymentSuccess(json, true);
            return;
        }

        if (retCode == RESULT_CANCELED) {
            loadQr.setVisibility(View.VISIBLE);
            qrPay.setVisibility(View.INVISIBLE);
            loadQr.setText(String.format(getString(R.string.get_qr_fail), retMessage));

            qrCancel.setVisibility(View.VISIBLE);
            qrAction.setVisibility(View.VISIBLE);
            return;
        }
        loadQr.setVisibility(View.INVISIBLE);
        qrPay.setVisibility(View.VISIBLE);
        Bitmap bitmap = QRCodeHelper
                .newInstance(mActivity)
                .setContent(qrCode)
                .setErrorCorrectionLevel(ErrorCorrectionLevel.Q)
                .setMargin(2)
                .getQRCOde();
        qrPay.setImageBitmap(bitmap);

        qrTime = System.currentTimeMillis();

        if (mPaymentStatusTask != null && mPaymentStatusTask.getStatus() != AsyncTask.Status.FINISHED)
            mPaymentStatusTask.cancel(true);
        mPaymentStatusTask = new PaymentStatusTask(mResultUrl, mQrDuration, this, this).execute();

        Log.d(TAG, retMessage);
    }

    @Override
    public void beforePayRequest(JSONObject param) {
        qrTime = 0;
        if (mPaymentStatusTask != null && mPaymentStatusTask.getStatus() != AsyncTask.Status.FINISHED)
            mPaymentStatusTask.cancel(true);
        loadQr.setText("正在加载收款码......");
        tickText.setText("");
        qrPay.setVisibility(View.INVISIBLE);
        loadQr.setVisibility(View.VISIBLE);
        tickText.setVisibility(View.VISIBLE);

    }

    @Override
    public void onPayRequestTick(long millisUntilFinished) {
        tickText.setText(String.format("%d", millisUntilFinished / 1000));
    }

    @Override
    public synchronized void onPaymentStatus(String output) {

        tickText.setText("");
        tickText.setVisibility(View.INVISIBLE);

        JSONObject result = null;
        try {
            Log.d(TAG,  "get response : " + (output == null ? "null" : output));
            result = new JSONObject(output);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 没有正确结果
        if (result == null || !result.optString("error", "").equals("")
                || !result.optString("sessionstatus", "").equals("成功")) {
            onPaymentFailed(result);
            return;
        }

        onPaymentSuccess(result, false);

    }

    @Override
    public void beforePaymentStatus(String url) {
        tickText.setText("");
        tickText.setVisibility(View.VISIBLE);
    }

    @Override
    public void onPayementStatustTick(long millisUntilFinished) {
        tickText.setText(String.format("%d", millisUntilFinished / 1000));
    }

    private void onPaymentFailed(final JSONObject result) {

        loadQr.setVisibility(View.VISIBLE);
        qrPay.setVisibility(View.INVISIBLE);
        String workflowHint = parameter.optString("scene", "").equals("结算")
                ? getString(R.string.settle_payment_status_unknown)
                : getString(R.string.cash_in_payment_status_unknown);

        String message =
                // 网络问题
                result == null
                ? String.format(getString(R.string.payment_status_network_error), workflowHint)
                // 查询返回错误信息
                : !result.optString("error", "").equals("")
                ? String.format(getString(R.string.payment_status_error), result.optString("error", ""), workflowHint)
                // 单据关闭/失败/等待支付超时
                : String.format(getString(R.string.payment_status_pending), workflowHint);

        qrAction.setVisibility(View.VISIBLE);
        qrMannualChecked.setVisibility(View.VISIBLE);

        loadQr.setText(message);

        return;
    }

    private void onPaymentSuccess(JSONObject result, Boolean duplicate) {
        qrTime = -1;
        loadQr.setVisibility(View.VISIBLE);
        qrPay.setVisibility(View.INVISIBLE);
        double order_amount = 0;

        // 保存成功状态
        try {
            JSONObject output = new JSONObject(getString(R.string.qr_confirm_result));
            JSONArray content = result.optJSONArray("content");
            JSONArray scanPay = output.optJSONArray("scan_pay");

            int i;
            for(i = 0; i < content.length(); i++ ) {
                scanPay.put(i,
                        new JSONObject()
                                .put("guid", content.optJSONObject(i).optString("guid"))
                                .put("count", content.optJSONObject(i).optDouble("order_amount", 0)));
                order_amount += content.optJSONObject(i).optDouble("order_amount", 0);
            }
            output.putOpt("scan_pay", scanPay);
            mResultJson.putOpt("output", output);
            Log.d(TAG, "success result : \n" + mResultJson.toString(4));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // 支付成功，强制切回扫码支付
        // 不允许切换到现金支付
        loadQr.setText(
                duplicate
                        ? String.format(getString(R.string.pay_duplicate), order_amount)
                        : String.format(getString(R.string.pay_done), order_amount));

        payByQr.callOnClick();
        payByCash.setClickable(false);
        payByCash.setTypeface(payByCash.getTypeface(), Typeface.ITALIC);

        findViewById(R.id.qr_done).setVisibility(View.VISIBLE);

    }

    private void setClickListener() {
        // back button
        findViewById(R.id.back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mActivity.finish();
            }
        });
        payByCash.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                payByCash.setBackgroundColor(Color.WHITE);
                payByCash.setTextColor(Color.parseColor("#FF6600"));
                payByQr.setBackgroundColor(Color.GRAY);
                payByQr.setTextColor(Color.BLACK);
                mCurrentMode = R.id.pay_cash;

                findViewById(R.id.cash_layout).setVisibility(View.VISIBLE);
                findViewById(R.id.qr_layout).setVisibility(View.INVISIBLE);
                showDescriptionLayout(getString(R.string.cash_pay_description));
            }
        });

        payByQr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                payByCash.setBackgroundColor(Color.GRAY);
                payByCash.setTextColor(Color.BLACK);
                payByQr.setBackgroundColor(Color.WHITE);
                payByQr.setTextColor(Color.parseColor("#FF6600"));
                mCurrentMode = R.id.pay_qr;

                findViewById(R.id.qr_layout).setVisibility(View.VISIBLE);
                findViewById(R.id.cash_layout).setVisibility(View.INVISIBLE);
                showDescriptionLayout(getString(R.string.qr_pay_description));

                refresh();
            }
        });

        qrCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mActivity.finish();
            }
        });

        findViewById(R.id.qr_done).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mActivity.finish();
            }
        });

        // 人工确认，没有相应的系统记录，需后台对账
        qrMannualChecked.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    mResultJson.putOpt("output", new JSONObject(getString(R.string.cash_confirm_result)));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                mActivity.finish();
            }
        });

        qrAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new GetPayRequestTask(parameter, mActivity, JXBCashierActivity.this).execute();
                qrAction.setVisibility(View.INVISIBLE);
                qrCancel.setVisibility(View.INVISIBLE);
                qrMannualChecked.setVisibility(View.INVISIBLE);
                //loadQr.setClickable(false);
            }
        });

        ((Button) findViewById(R.id.cash_cancel)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mActivity.finish();
            }
        });
        ((Button) findViewById(R.id.cash_confirm)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    mResultJson.putOpt("output", new JSONObject(getString(R.string.cash_confirm_result)));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                mActivity.finish();
            }
        });
        // tips
        findViewById(R.id.confirm_acknowledge).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                findViewById(R.id.pay_desc_layout).setVisibility(View.INVISIBLE);
                refresh();
            }
        });
        findViewById(R.id.no_more).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String key = String.format("%d",mCurrentMode);
                findViewById(R.id.pay_desc_layout).setVisibility(View.INVISIBLE);
                SharedPreferences pref = getApplicationContext().getSharedPreferences("pay_description_no_more", 0);
                SharedPreferences.Editor editor = pref.edit();
                editor.putBoolean(key, true);
                editor.commit();
                refresh();
            }
        });
    }

    private void showDescriptionLayout(String desc)
    {
        String key = String.format("%d",mCurrentMode);
        ((TextView)findViewById(R.id.pay_description)).setText(desc);

        // Already shown in current operation or user choose no more
        if(mPayDescShown.optBoolean(key, false )
                || getApplicationContext().getSharedPreferences("pay_description_no_more", 0)
                .getBoolean(key, false)) {
            // Close it incase already there
            findViewById(R.id.pay_desc_layout).setVisibility(View.INVISIBLE);
            return;
        }

        findViewById(R.id.pay_desc_layout).setVisibility(View.VISIBLE);
        try {
            mPayDescShown.put(key, true );
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}

