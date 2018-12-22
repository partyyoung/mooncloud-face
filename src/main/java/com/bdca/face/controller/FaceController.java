package com.bdca.face.controller;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import com.alibaba.fastjson.JSON;
import com.bdca.face.FaceResponse;
import com.bdca.face.service.FaceService;

@RestController
@RequestMapping(value = { "/bdca/face/v1" })
public class FaceController {

	private final static Logger LOGGER = LoggerFactory.getLogger(FaceController.class);

	@Autowired
	FaceService faceService;

	@RequestMapping()
	public Object home() throws IOException {
		RequestAttributes ra = RequestContextHolder.getRequestAttributes();
		ServletRequestAttributes sra = (ServletRequestAttributes) ra;
		HttpServletResponse response = sra.getResponse();
		response.addHeader("Access-Control-Allow-Origin", "*");

		FaceResponse faceResponse = new FaceResponse();
		faceResponse.setBody("face");
		return faceResponse;
	}

	/**
	 * 
	 * @param file1
	 * @param file2
	 * @return
	 * @throws IOException
	 */
	@RequestMapping(value = "/comparison", method = { RequestMethod.POST, RequestMethod.GET })
	public Object comparison(MultipartFile file1, MultipartFile file2) throws IOException {
		RequestAttributes ra = RequestContextHolder.getRequestAttributes();
		ServletRequestAttributes sra = (ServletRequestAttributes) ra;
		HttpServletResponse response = sra.getResponse();
		response.addHeader("Access-Control-Allow-Origin", "*");

		FaceResponse faceResponse = new FaceResponse();
		try {
			Assert.isTrue(file1 != null && !file1.isEmpty(), "file1为空");
			Assert.isTrue(file2 != null && !file2.isEmpty(), "file2为空");
			Map res = new HashMap(1);
			res.put("score", faceService.comparison(file1, file2));
			faceResponse.setBody(res);
		} catch (IllegalArgumentException e) {
			faceResponse.setErrorCode(faceResponse.ERROR_CODE);
			faceResponse.setMsg(e.toString());
		} catch (IOException e) {
			faceResponse.setErrorCode(faceResponse.ERROR_CODE);
			faceResponse.setMsg(e.toString());
		}
		LOGGER.info(JSON.toJSONString(faceResponse));
		return faceResponse;
	}

	/**
	 * 
	 * @param file
	 * @param id
	 * @return
	 * @throws IOException
	 */
	@RequestMapping(value = "/match", method = { RequestMethod.POST, RequestMethod.GET })
	public Object match(MultipartFile image, String idcard) throws IOException {
		RequestAttributes ra = RequestContextHolder.getRequestAttributes();
		ServletRequestAttributes sra = (ServletRequestAttributes) ra;
		HttpServletResponse response = sra.getResponse();
		response.addHeader("Access-Control-Allow-Origin", "*");

		FaceResponse faceResponse = new FaceResponse();
		faceResponse.setMsg("该身份证号码未提交身份证照片");
		faceResponse.setCode("404");
		try {
			Assert.isTrue(image != null && !image.isEmpty(), "file");
			Map res = new HashMap(1);
			res.put("match", faceService.query(image).equals(idcard));
			faceResponse.setBody(res);
			faceResponse.setMsg("比对成功");
			faceResponse.setCode("200");
		} catch (IllegalArgumentException e) {
			faceResponse.setErrorCode(faceResponse.ERROR_CODE);
			faceResponse.setMsg(e.toString());
		} catch (IOException e) {
			faceResponse.setErrorCode(faceResponse.ERROR_CODE);
			faceResponse.setMsg(e.toString());
		}
		LOGGER.info(JSON.toJSONString(faceResponse));
		return faceResponse;
	}

	/**
	 * @param file
	 * @param id
	 * @return
	 * @throws IOException
	 */
	@RequestMapping(value = "/add", method = { RequestMethod.POST, RequestMethod.GET })
	public Object add(MultipartFile file, String id) throws IOException {
		RequestAttributes ra = RequestContextHolder.getRequestAttributes();
		ServletRequestAttributes sra = (ServletRequestAttributes) ra;
		HttpServletResponse response = sra.getResponse();
		response.addHeader("Access-Control-Allow-Origin", "*");

		FaceResponse faceResponse = new FaceResponse();
		try {
			Assert.isTrue(file != null && !file.isEmpty(), "file为空");
			Assert.isTrue(id != null && !id.isEmpty() && !"NONE".equals(id), "id为空");
			faceResponse.setBody(faceService.add(file, id));
		} catch (IllegalArgumentException e) {
			faceResponse.setErrorCode(faceResponse.ERROR_CODE);
			faceResponse.setMsg(e.toString());
		} catch (IOException e) {
			faceResponse.setErrorCode(faceResponse.ERROR_CODE);
			faceResponse.setMsg(e.toString());
		}
		LOGGER.info(JSON.toJSONString(faceResponse));
		return faceResponse;
	}

	/**
	 * @param id
	 * @return
	 * @throws IOException
	 */
	@RequestMapping(value = "/delete", method = { RequestMethod.POST, RequestMethod.GET })
	public Object delete(String id) throws IOException {
		RequestAttributes ra = RequestContextHolder.getRequestAttributes();
		ServletRequestAttributes sra = (ServletRequestAttributes) ra;
		HttpServletResponse response = sra.getResponse();
		response.addHeader("Access-Control-Allow-Origin", "*");

		FaceResponse faceResponse = new FaceResponse();
		try {
			Assert.isTrue(id != null && !id.isEmpty(), "id为空");
			faceResponse.setBody(faceService.delete(id));
		} catch (IllegalArgumentException e) {
			faceResponse.setErrorCode(faceResponse.ERROR_CODE);
			faceResponse.setMsg(e.toString());
		} catch (IOException e) {
			faceResponse.setErrorCode(faceResponse.ERROR_CODE);
			faceResponse.setMsg(e.toString());
		}
		LOGGER.info(JSON.toJSONString(faceResponse));
		return faceResponse;
	}

	/**
	 * @param file
	 * @return
	 * @throws IOException
	 */
	@RequestMapping(value = "/query", method = { RequestMethod.POST, RequestMethod.GET })
	public Object query(MultipartFile file) throws IOException {
		RequestAttributes ra = RequestContextHolder.getRequestAttributes();
		ServletRequestAttributes sra = (ServletRequestAttributes) ra;
		HttpServletResponse response = sra.getResponse();
		response.addHeader("Access-Control-Allow-Origin", "*");

		FaceResponse faceResponse = new FaceResponse();
		try {
			Assert.isTrue(file != null && !file.isEmpty(), "file");
			Map res = new HashMap(1);
			res.put("id", faceService.query(file));
			faceResponse.setBody(res);
		} catch (IllegalArgumentException e) {
			faceResponse.setErrorCode(faceResponse.ERROR_CODE);
			faceResponse.setMsg(e.toString());
		} catch (IOException e) {
			faceResponse.setErrorCode(faceResponse.ERROR_CODE);
			faceResponse.setMsg(e.toString());
		}
		LOGGER.info(JSON.toJSONString(faceResponse));
		return faceResponse;
	}

}
