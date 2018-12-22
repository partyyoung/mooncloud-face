
## 在 64 位的 Linux 系统中安装 32 位的 JDK 环境
apt-get install libc6-i386 lib32gcc1 lib32stdc++6 lib32z1 lib32ncurses5

## Startup
```
nohup java -jar bdca-face-0.0.1-SNAPSHOT.jar --server.port=12121 &
```

## APIs
服务地址：http://127.0.0.1:12121/bdca/face/v1

1. [人脸识别接口](#人脸识别接口)

## 人脸识别接口
| id | 类型 | 接口                       | 说明                    |
| -- | ---- | -------------------------- | ----------------------- |
| 1. | GET/POST | [/comparison](#comparison) | 人脸比对  |
| 2. | GET/POST | [/add](#add) | 人脸注册 |
| 3. | GET/POST | [/delete](#delete) | 删除人脸 |
| 4. | GET/POST | [/query](#query) | 人脸查找  |

### /comparison
用户提供自拍照片两张，应用平台对提供的照片进行人脸比对，返回比对结果。

#### 业务参数
file1: 第一张人脸图像
file2: 第二张人脸图像

#### 返回参数
* body: 返回相似度值

#### 返回示例
```
{
	"errorCode": null,
	"msg": null,
	"body": "0.9999",
	"success": true
}
```

### /add
注册人脸信息。

#### 业务参数
file: 用户人脸图像
id: 用户id

#### 返回参数
* body: 用户id

#### 返回示例
```
{
	"errorCode": null,
	"msg": null,
	"body": "1",
	"success": true
}
```

### /delete
删除人脸信息。

#### 业务参数
id: 用户id

#### 返回参数
* body: 用户id

#### 返回示例
```
{
	"errorCode": null,
	"msg": null,
	"body": "1",
	"success": true
}
```

### /query
查询人脸ID。

#### 业务参数
file: 用户人脸图像

#### 返回参数
* body: 用户id

#### 返回示例
```
{
	"errorCode": null,
	"msg": null,
	"body": "1",
	"success": true
}
```

## API调用
### JAVA
```
		<!-- https://mvnrepository.com/artifact/org.apache.httpcomponents/httpmime -->
		<dependency>
			<groupId>org.apache.httpcomponents</groupId>
			<artifactId>httpmime</artifactId>
			<version>4.5.5</version>
		</dependency>
```

```
	private static String HOST = "http://localhost:8080";
	
	public static Object facecomparison(String filePathA, String filePathB) throws IOException {
		// 1. 创建上传需要的元素类型

		// 1.1 装载本地上传图片的文件
		File file1 = new File(filePathA);
		Assert.check(file1.exists(), "文件 not exists");
		FileBody fileBody1 = new FileBody(file1);

		File file2 = new File(filePathB);
		Assert.check(file2.exists(), "文件 not exists");
		FileBody fileBody2 = new FileBody(file2);

		// 2. 将所有需要上传元素打包成HttpEntity对象
		MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
		multipartEntityBuilder.addPart("file1", fileBody1);
		multipartEntityBuilder.addPart("file2", fileBody2);

		HttpEntity reqEntity = multipartEntityBuilder.build();
		LOGGER.debug("打包数据完成");
		return _post(reqEntity, HOST + "/bdca/face/v1/comparison", file1.length() + file2.length());
	}

	public static Object faceadd(String filePath, String id) throws IOException {
		// 1. 创建上传需要的元素类型

		// 1.1 装载本地上传图片的文件
		File file = new File(filePath);
		Assert.check(file.exists(), "文件 not exists");
		FileBody fileBody = new FileBody(file);

		// 2. 将所有需要上传元素打包成HttpEntity对象
		MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
		multipartEntityBuilder.addPart("file", fileBody);
		multipartEntityBuilder.addPart("id", new StringBody(id, ContentType.DEFAULT_TEXT));

		HttpEntity reqEntity = multipartEntityBuilder.build();
		LOGGER.debug("打包数据完成");
		return _post(reqEntity, HOST + "/bdca/face/v1/add", file.length());
	}

	public static Object facequery(String filePath) throws IOException {
		// 1. 创建上传需要的元素类型

		// 1.1 装载本地上传图片的文件
		File file = new File(filePath);
		Assert.check(file.exists(), "文件 not exists");
		FileBody fileBody = new FileBody(file);

		// 2. 将所有需要上传元素打包成HttpEntity对象
		MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
		multipartEntityBuilder.addPart("file", fileBody);

		HttpEntity reqEntity = multipartEntityBuilder.build();
		LOGGER.debug("打包数据完成");
		return _post(reqEntity, HOST + "/bdca/face/v1/query", file.length());
	}

	private static Object _post(final HttpEntity reqEntity, final String url, final long length) throws IOException {
		Long start = System.currentTimeMillis();
		Map<String, Object> ret = new HashMap<String, Object>();
		ret.put("start", start);
		// 3. 创建HttpPost对象，用于包含信息发送post消息
		HttpPost httpPost = new HttpPost(url);
		httpPost.setEntity(reqEntity);
		LOGGER.debug("创建post请求并装载好打包数据");
		// System.out.println("创建post请求并装载好打包数据");
		// 4. 创建HttpClient对象，传入httpPost执行发送网络请求的动作
		CloseableHttpClient httpClient = HttpClients.createDefault();
		CloseableHttpResponse response = httpClient.execute(httpPost);
		LOGGER.debug("发送post请求并获取结果");
		// System.out.println("发送post请求并获取结果");
		// 5. 获取返回的实体内容对象并解析内容
		HttpEntity resultEntity = response.getEntity();
		String responseMessage = "";
		try {
			LOGGER.debug("开始解析结果");
			// System.out.println("开始解析结果");
			if (resultEntity != null) {
				InputStream is = resultEntity.getContent();
				BufferedReader br = new BufferedReader(new InputStreamReader(is));
				StringBuffer sb = new StringBuffer();
				String line = "";
				while ((line = br.readLine()) != null) {
					sb.append(line);
				}
				responseMessage = sb.toString();
				LOGGER.debug("解析完成，解析内容为" + responseMessage);
				// System.out.println("解析完成，解析内容为" + responseMessage);
			}
			EntityUtils.consume(resultEntity);
		} finally {
			if (null != response) {
				response.close();
			}
		}
		Long end = System.currentTimeMillis();
		ret.put("length", length);
		ret.put("end", end);
		ret.put("taken", end - start);
		ret.put("response", responseMessage);
		return ret;
	}
```
