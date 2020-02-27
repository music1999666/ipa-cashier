# 提供收银台核心功能

支付方式：APP/QR/H5/公众号支付  
支付渠道：微信/支付宝/云闪付

# 使用环境
  
  提供三个环境  
  dev/demo/product  分别对应开发，演示，生产环境 
  
# 调用参数

  pay_type: 微信/支付宝/云闪付/c2b聚合支付
  method:   h5pay/qrpay/apppay  
  amount:   收款金额，以元为单位，两位小数 
  sandbox:  子商户二级域名，一般为注册手机号  
  billnumber: 订单号，要求在一个子商户中唯一   
  notifyurl:  调用方提供的回调地址用于接收支付状态通知，POST方式，json参数 application/json; charset=utf-8  
  returnurl:  支付后跳转页面，支付方式为h5pay时使用 
  sign:       签名信息
  
# 返回结果

  公众号支付
  
    {
      "url": "https://xxx?...",
      "method": "get",
      "result_url": "http://xxx?...",
      "sign":"XXXXX..."
    }    
    调用者在微信中跳转到url指定页面，可通过result_url自主查询支付结果，或者通过传递的notifyurl接收通知消息

  h5支付  
  
    {
      "url": "https://xxx?...",
      "method": "get",
      "result_url": "http://xxx?...",
      "sign":"XXXXX..."
    }
    调用者在浏览器中跳转到url指定页面，可通过result_url自主查询支付结果，或者通过传递的notifyurl接收通知消息

  扫码支付
  
    {
      "qrcode": "https://xxx?id=31942001154593417000039638",
      "result_url": "http://xxx?...",
      "sign":"XXXXX..."
    }
    调用者在将qrcode内容编码，并显示在界面中，可通过result_url自主查询支付结果，或者通过传递的notifyurl接收通知消息

  APP支付
  
    {
    "app_pay_request": {"xxx":"xxxx"},
    "result_url": "http://xxx?...",
      "sign":"XXXXX..."
    }
    调用者在将app_pay_reqeust内容作为参数，调用相应的APP支付sdk，可通过result_url自主查询支付结果，或者通过传递的notifyurl接收通知消息
    
  错误信息
  
    如果调用发生错误，同样用json返回结果：
    {
      "error":"没有注册的用户"
    }
    调用者检查error内容是否为空字符串/null，否则就表示获得了正确的结果
    
# 支付通知
    post，body：json
    {
      "sessionstatus":"成功|失败|关闭|等待|退款", // 交易状态
      "sessionid":"1234xxxxx",                  // 支付订单号
      "sessionendtime":"2020-1-20 09:01:29",    // 支付时间
      "transaction_no":"1234xxx",               // 支付机构流水号
      "billnumber":"2345xxx",                   // 客户购买订单号
      "receipt_amount":1000.00,                 // 商户实收金额
      "order_amount":1000.00,                   // 商户订单金额
      "settle_date":"2020-1-20",                // 结算日期
      "sandbox":"189xxx"                        // 子商户二级域名，一般为注册手机号
      "sign":"XXXXX......"                      // 通知签名
    }
    
# 查询结果
    支付请求返回以下查询url，也可以自行拼装，提供sandbox/billnumber为参数
    http://xxxx/webapi/asset/outertransfer/status?billnumber=202001070058150000006&sandbox=123456789&sign=XXXXXXX

    正常返回
    {
      "sessionstatus":"成功|失败|关闭|等待|退款", // 交易状态
      "sessionid":"1234xxxxx",                  // 支付订单号
      "billnumber":"2345xxx",                   // 客户购买订单号
      "sessionendtime":"2020-1-20 09:01:29",    // 支付时间
      "order_amount":1000.00,                   // 商户订单金额
      "receipt_amount":1000.00                  // 商户实收金额
      "sign":"XXXXX......"                       // 签名
    }
    错误
    {
      "error": "找不到订单"
    } 

#调用/回调签名算法

  签名生成的通用步骤如下：
  
  第一步，设所有发送或者接收到的数据为集合M，将集合M内非空参数值的参数按照参数名ASCII码从小到大排序（字典序），使用URL键值对的格式（即key1=value1&key2=value2…）拼接成字符串。
  
  第二步，在stringA最后拼接上应用key得到stringSignTemp字符串，并对stringSignTemp进行MD5运算，再将得到的字符串所有字符转换为大写，得到sign值signValue。
  
  stringSignTemp="xxx=yyy&key=key"
  sign=MD5(stringSignTemp).toUpperCase()
