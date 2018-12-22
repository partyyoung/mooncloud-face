package com.bdca.face.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.bdca.AFD_FSDKLibrary;
import com.bdca.AFD_FSDK_FACERES;
import com.bdca.AFD_FSDK_Version;
import com.bdca.AFR_FSDKLibrary;
import com.bdca.AFR_FSDK_FACEINPUT;
import com.bdca.AFR_FSDK_FACEMODEL;
import com.bdca.AFR_FSDK_Version;
import com.bdca.ASVLOFFSCREEN;
import com.bdca.ASVL_COLOR_FORMAT;
import com.bdca.CLibrary;
import com.bdca.FaceInfo;
import com.bdca.MRECT;
import com.bdca._AFD_FSDK_OrientPriority;
import com.bdca.face.util.MD5Hash;
import com.bdca.utils.BufferInfo;
import com.bdca.utils.ImageLoader;
import com.sun.jna.Memory;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.FloatByReference;
import com.sun.jna.ptr.PointerByReference;

@Service
@ConfigurationProperties(prefix = "face-service")
public class FaceService {

	private final static Logger LOGGER = LoggerFactory.getLogger(FaceService.class);

	private String appid = "C1c6gSSLDF3eR3s6QCKKL9Arf4aGSZeKxsYcm6mWKGME";
	private String fdSdkKey = "2WRgyyUEbRsS1zmv2iJx6UbEzkeX9HD9AeoLWXZscwpz";
	private String frSdkKey = "2WRgyyUEbRsS1zmv2iJx6UbNA9uektEPzgZLYiEpwWDv";

	public static final int FD_WORKBUF_SIZE = 20 * 1024 * 1024;
	public static final int FR_WORKBUF_SIZE = 40 * 1024 * 1024;
	public static final int MAX_FACE_NUM = 50;

	public static final boolean bUseRAWFile = false;
	public static final boolean bUseBGRToEngine = true;

	// 引擎队列
	// private Pointer hFDEngine;
	// private Pointer hFREngine;
	private int maxEngineNum = 128;
	private static List<Pointer> FDEngine_LIST = null;
	private static List<Pointer> FREngine_LIST = null;
	private static int FDEngine_INDEX = 0;
	private static int FREngine_INDEX = 0;

	// 人脸库
	private static final int FACE_Initial_Capacity = 1024;
	private static final ConcurrentHashMap<String, List<AFR_FSDK_FACEMODEL>> FACE_ID_FEATURE = new ConcurrentHashMap<String, List<AFR_FSDK_FACEMODEL>>(
			FACE_Initial_Capacity);
	private String faceImagePath = "bdca-face";

	/**
	 * @param faceImagePath
	 */
	public void setFaceImagePath(String faceImagePath) {
		this.faceImagePath = faceImagePath;
	}

	public int getMaxEngineNum() {
		return maxEngineNum;
	}

	public void setMaxEngineNum(int maxEngineNum) {
		this.maxEngineNum = maxEngineNum;
	}

	@PostConstruct
	private void init() {
		FDEngine_LIST = new ArrayList<Pointer>(this.maxEngineNum);
		FREngine_LIST = new ArrayList<Pointer>(this.maxEngineNum);
		for (int NUM = 0; NUM < this.maxEngineNum; NUM++) {
			// init Engine
			Pointer pFDWorkMem = CLibrary.INSTANCE.malloc(FD_WORKBUF_SIZE);
			Pointer pFRWorkMem = CLibrary.INSTANCE.malloc(FR_WORKBUF_SIZE);

			PointerByReference phFDEngine = new PointerByReference();
			NativeLong ret = AFD_FSDKLibrary.INSTANCE.AFD_FSDK_InitialFaceEngine(appid, fdSdkKey, pFDWorkMem,
					FD_WORKBUF_SIZE, phFDEngine, _AFD_FSDK_OrientPriority.AFD_FSDK_OPF_0_HIGHER_EXT, 32, MAX_FACE_NUM);
			if (ret.longValue() != 0) {
				CLibrary.INSTANCE.free(pFDWorkMem);
				CLibrary.INSTANCE.free(pFRWorkMem);
				System.out.println(String.format("AFD_FSDK_InitialFaceEngine ret 0x%x", ret.longValue()));
				System.exit(0);
			}

			// print FDEngine version
			Pointer hFDEngine = phFDEngine.getValue();
			AFD_FSDK_Version versionFD = AFD_FSDKLibrary.INSTANCE.AFD_FSDK_GetVersion(hFDEngine);
			// System.out.println(String.format("%d %d %d %d", versionFD.lCodebase,
			// versionFD.lMajor, versionFD.lMinor,
			// versionFD.lBuild));
			// System.out.println(versionFD.BuildDate);

			PointerByReference phFREngine = new PointerByReference();
			ret = AFR_FSDKLibrary.INSTANCE.AFR_FSDK_InitialEngine(appid, frSdkKey, pFRWorkMem, FR_WORKBUF_SIZE,
					phFREngine);
			if (ret.longValue() != 0) {
				AFD_FSDKLibrary.INSTANCE.AFD_FSDK_UninitialFaceEngine(hFDEngine);
				CLibrary.INSTANCE.free(pFDWorkMem);
				CLibrary.INSTANCE.free(pFRWorkMem);
				System.out.println(String.format("AFR_FSDK_InitialEngine ret 0x%x", ret.longValue()));
				System.exit(0);
			}

			// print FREngine version
			Pointer hFREngine = phFREngine.getValue();
			AFR_FSDK_Version versionFR = AFR_FSDKLibrary.INSTANCE.AFR_FSDK_GetVersion(hFREngine);
			// System.out.println(String.format("%d %d %d %d", versionFR.lCodebase,
			// versionFR.lMajor, versionFR.lMinor,
			// versionFR.lBuild));
			// System.out.println(versionFR.BuildDate);

			LOGGER.info("引擎队列初始化...\t" + NUM + "/" + this.maxEngineNum);
			FDEngine_LIST.add(hFDEngine);
			FREngine_LIST.add(hFREngine);
		}
		LOGGER.info("引擎队列初始化完成\t" + this.maxEngineNum);
		loadFace();
	}

	private Pointer getFDEngine() {
		return FDEngine_LIST.get((FDEngine_INDEX++) % this.maxEngineNum);
	}

	private Pointer getFREngine() {
		return FREngine_LIST.get((FREngine_INDEX++) % this.maxEngineNum);
	}

	private void loadFace() {
		File path = new File(this.faceImagePath + "/face/");
		if (!path.exists()) {
			path.mkdirs();
		}
		File[] fileList = path.listFiles();
		for (File idPath : fileList) {
			if (idPath.isDirectory()) {
				String id = idPath.getName();
				File[] faceImageList = idPath.listFiles();
				for (File faceImage : faceImageList) {
					if (faceImage.isFile()) {
						// load Image Data
						ASVLOFFSCREEN inputImg = loadImage(faceImage.getAbsolutePath());
						// Detect Face
						FaceInfo[] faceInfos = doFaceDetection(getFDEngine(), inputImg);
						if (faceInfos.length < 1) {
							continue;
						}
						// Extract Face Feature
						AFR_FSDK_FACEMODEL faceFeature = extractFRFeature(getFREngine(), inputImg, faceInfos[0]);
						if (faceFeature == null) {
							continue;
						}

						if (FACE_ID_FEATURE.contains(id)) {
							FACE_ID_FEATURE.get(id).add(faceFeature);
						} else {
							List<AFR_FSDK_FACEMODEL> faceFeatures = new LinkedList<AFR_FSDK_FACEMODEL>();
							faceFeatures.add(faceFeature);
							FACE_ID_FEATURE.put(id, faceFeatures);
						}

						LOGGER.info("人脸库初始化...\t" + id + " " + faceImage.getName());
					}
				}
			}
		}
		LOGGER.info("人脸库初始化完成\t" + FACE_ID_FEATURE.size());
	}

	public Object add(MultipartFile file, String id) throws IOException {
		// load Image Data
		ASVLOFFSCREEN inputImg = loadImage(file.getInputStream());

		// Detect Face
		FaceInfo[] faceInfos = doFaceDetection(getFDEngine(), inputImg);
		if (faceInfos.length < 1) {
			throw new IOException("no face in Image");
		}

		// Extract Face Feature
		AFR_FSDK_FACEMODEL faceFeature = extractFRFeature(getFREngine(), inputImg, faceInfos[0]);
		if (faceFeature == null) {
			throw new IOException("extract face feature in Image failed");
		}

		if (FACE_ID_FEATURE.contains(id)) {
			FACE_ID_FEATURE.get(id).add(faceFeature);
		} else {
			List<AFR_FSDK_FACEMODEL> faceFeatures = new LinkedList<AFR_FSDK_FACEMODEL>();
			faceFeatures.add(faceFeature);
			FACE_ID_FEATURE.put(id, faceFeatures);
		}

		transferTo(file, new File(this.faceImagePath + "/face/" + id).getAbsolutePath());

		LOGGER.info("人脸库新增完成\t" + FACE_ID_FEATURE.size());

		return id;
	}

	public Object delete(String id) throws IOException {
		FACE_ID_FEATURE.remove(id);
		File file = new File(this.faceImagePath + "/face/" + id);
		file.delete();

		LOGGER.info("人脸库删除完成\t" + FACE_ID_FEATURE.size());

		return id;
	}

	public Object query(MultipartFile file) throws IOException {
		Long start = System.currentTimeMillis();
		// load Image Data
		ASVLOFFSCREEN inputImg = loadImage(file.getInputStream());
		LOGGER.info("loadImage\t" + (System.currentTimeMillis() - start));
		start = System.currentTimeMillis();
		// Detect Face
		FaceInfo[] faceInfos = doFaceDetection(getFDEngine(), inputImg);
		if (faceInfos.length < 1) {
			throw new IOException("no face in Image");
		}
		LOGGER.info("doFaceDetection\t" + (System.currentTimeMillis() - start));
		start = System.currentTimeMillis();

		// Extract Face Feature
		AFR_FSDK_FACEMODEL faceFeatureA = extractFRFeature(getFREngine(), inputImg, faceInfos[0]);
		if (faceFeatureA == null) {
			throw new IOException("extract face feature in Image failed");
		}
		LOGGER.info("extractFRFeature\t" + (System.currentTimeMillis() - start));
		start = System.currentTimeMillis();

		String id = "NONE";
		FloatByReference fSimilScore = new FloatByReference(0.0f);
		for (Entry<String, List<AFR_FSDK_FACEMODEL>> face : FACE_ID_FEATURE.entrySet()) {
			List<AFR_FSDK_FACEMODEL> faceFeatures = face.getValue();
			for (AFR_FSDK_FACEMODEL faceFeatureB : faceFeatures) {
				fSimilScore.setValue(0);
				NativeLong ret = AFR_FSDKLibrary.INSTANCE.AFR_FSDK_FacePairMatching(getFREngine(), faceFeatureA,
						faceFeatureB, fSimilScore);
				LOGGER.info("FacePairMatching\t" + (System.currentTimeMillis() - start));
				start = System.currentTimeMillis();
				if (fSimilScore.getValue() > 0.5) {
					id = face.getKey();
					break;
				}
			}
		}
		faceFeatureA.freeUnmanaged();

		return id;
	}

	public Object comparison(MultipartFile file1, MultipartFile file2) throws IOException {
		// load Image Data
		ASVLOFFSCREEN inputImgA;
		ASVLOFFSCREEN inputImgB;
		inputImgA = loadImage(file1.getInputStream());
		inputImgB = loadImage(file2.getInputStream());

		float similarity = compareFaceSimilarity(getFDEngine(), getFREngine(), inputImgA, inputImgB);

		System.out.println(String.format("similarity between faceA and faceB is %f", similarity));
		System.out.println(String.format("similarity between faceA and faceB is %f", similarity = softMax(similarity)));

		return similarity;

	}

	private static String transferTo(MultipartFile multipartFile, String path) throws IOException {

		String fileName = multipartFile.getOriginalFilename();
		// InputStream inputStream = multipartFile.getInputStream();

		StringBuilder stringBuider = new StringBuilder();
		stringBuider.append(System.currentTimeMillis()).append("-").append(MD5Hash.digest(fileName).toString16());
		int extension = fileName.indexOf('.');
		if (extension > -1) {
			stringBuider.append(fileName.substring(extension));
		}
		fileName = stringBuider.toString();

		File file = new File(path + "/" + fileName);
		if (!file.getParentFile().exists()) {
			file.getParentFile().mkdirs();
		}

		multipartFile.transferTo(file);

		return file.getAbsolutePath();
	}

	// public Object comparison(String filePathA, String filePathB) {
	// // load Image Data
	// ASVLOFFSCREEN inputImgA;
	// ASVLOFFSCREEN inputImgB;
	// if (bUseRAWFile) {
	// int yuv_widthA = 640;
	// int yuv_heightA = 480;
	// int yuv_formatA = ASVL_COLOR_FORMAT.ASVL_PAF_I420;
	//
	// int yuv_widthB = 640;
	// int yuv_heightB = 480;
	// int yuv_formatB = ASVL_COLOR_FORMAT.ASVL_PAF_I420;
	//
	// inputImgA = loadRAWImage(filePathA, yuv_widthA, yuv_heightA, yuv_formatA);
	// inputImgB = loadRAWImage(filePathB, yuv_widthB, yuv_heightB, yuv_formatB);
	// } else {
	// inputImgA = loadImage(filePathA);
	// inputImgB = loadImage(filePathB);
	// }
	//
	// float similarity = compareFaceSimilarity(hFDEngine, hFREngine, inputImgA,
	// inputImgB);
	//
	// System.out.println(String.format("similarity between faceA and faceB is %f",
	// similarity));
	// System.out.println(String.format("similarity between faceA and faceB is %f",
	// similarity = softMax(similarity)));
	//
	// // release Engine
	// // AFD_FSDKLibrary.INSTANCE.AFD_FSDK_UninitialFaceEngine(hFDEngine);
	// // AFR_FSDKLibrary.INSTANCE.AFR_FSDK_UninitialEngine(hFREngine);
	// //
	// // CLibrary.INSTANCE.free(pFDWorkMem);
	// // CLibrary.INSTANCE.free(pFRWorkMem);
	//
	// return similarity;
	//
	// }

	public static float softMax(float x) {

		double y = Math.pow(x, 2) * 10 + x * 5;

		double s = 1 / (1 + Math.pow(Math.E, -y));

		return (float) (s * 2 - 1);
	}

	public static FaceInfo[] doFaceDetection(Pointer hFDEngine, ASVLOFFSCREEN inputImg) {
		FaceInfo[] faceInfo = new FaceInfo[0];

		PointerByReference ppFaceRes = new PointerByReference();
		NativeLong ret = AFD_FSDKLibrary.INSTANCE.AFD_FSDK_StillImageFaceDetection(hFDEngine, inputImg, ppFaceRes);
		if (ret.longValue() != 0) {
			System.out.println(String.format("AFD_FSDK_StillImageFaceDetection ret 0x%x", ret.longValue()));
			return faceInfo;
		}

		AFD_FSDK_FACERES faceRes = new AFD_FSDK_FACERES(ppFaceRes.getValue());
		if (faceRes.nFace > 0) {
			faceInfo = new FaceInfo[faceRes.nFace];
			for (int i = 0; i < faceRes.nFace; i++) {
				MRECT rect = new MRECT(
						new Pointer(Pointer.nativeValue(faceRes.rcFace.getPointer()) + faceRes.rcFace.size() * i));
				int orient = faceRes.lfaceOrient.getPointer().getInt(i * 4);
				faceInfo[i] = new FaceInfo();

				faceInfo[i].left = rect.left;
				faceInfo[i].top = rect.top;
				faceInfo[i].right = rect.right;
				faceInfo[i].bottom = rect.bottom;
				faceInfo[i].orient = orient;

				System.out.println(String.format("%d (%d %d %d %d) orient %d", i, rect.left, rect.top, rect.right,
						rect.bottom, orient));
			}
		}
		return faceInfo;
	}

	public static AFR_FSDK_FACEMODEL extractFRFeature(Pointer hFREngine, ASVLOFFSCREEN inputImg, FaceInfo faceInfo) {

		AFR_FSDK_FACEINPUT faceinput = new AFR_FSDK_FACEINPUT();
		faceinput.lOrient = faceInfo.orient;
		faceinput.rcFace.left = faceInfo.left;
		faceinput.rcFace.top = faceInfo.top;
		faceinput.rcFace.right = faceInfo.right;
		faceinput.rcFace.bottom = faceInfo.bottom;

		AFR_FSDK_FACEMODEL faceFeature = new AFR_FSDK_FACEMODEL();
		NativeLong ret = AFR_FSDKLibrary.INSTANCE.AFR_FSDK_ExtractFRFeature(hFREngine, inputImg, faceinput,
				faceFeature);
		if (ret.longValue() != 0) {
			System.out.println(String.format("AFR_FSDK_ExtractFRFeature ret 0x%x", ret.longValue()));
			return null;
		}

		try {
			return faceFeature.deepCopy();
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public static float compareFaceSimilarity(Pointer hFDEngine, Pointer hFREngine, ASVLOFFSCREEN inputImgA,
			ASVLOFFSCREEN inputImgB) {
		// Do Face Detect
		FaceInfo[] faceInfosA = doFaceDetection(hFDEngine, inputImgA);
		if (faceInfosA.length < 1) {
			System.out.println("no face in Image A ");
			return 0.0f;
		}

		FaceInfo[] faceInfosB = doFaceDetection(hFDEngine, inputImgB);
		if (faceInfosB.length < 1) {
			System.out.println("no face in Image B ");
			return 0.0f;
		}

		// Extract Face Feature
		AFR_FSDK_FACEMODEL faceFeatureA = extractFRFeature(hFREngine, inputImgA, faceInfosA[0]);
		if (faceFeatureA == null) {
			System.out.println("extract face feature in Image A failed");
			return 0.0f;
		}

		AFR_FSDK_FACEMODEL faceFeatureB = extractFRFeature(hFREngine, inputImgB, faceInfosB[0]);
		if (faceFeatureB == null) {
			System.out.println("extract face feature in Image B failed");
			faceFeatureA.freeUnmanaged();
			return 0.0f;
		}

		// calc similarity between faceA and faceB
		FloatByReference fSimilScore = new FloatByReference(0.0f);
		NativeLong ret = AFR_FSDKLibrary.INSTANCE.AFR_FSDK_FacePairMatching(hFREngine, faceFeatureA, faceFeatureB,
				fSimilScore);
		faceFeatureA.freeUnmanaged();
		faceFeatureB.freeUnmanaged();
		if (ret.longValue() != 0) {
			System.out.println(String.format("AFR_FSDK_FacePairMatching failed:ret 0x%x", ret.longValue()));
			return 0.0f;
		}
		return fSimilScore.getValue();
	}

	public static ASVLOFFSCREEN loadRAWImage(String yuv_filePath, int yuv_width, int yuv_height, int yuv_format) {
		int yuv_rawdata_size = 0;

		ASVLOFFSCREEN inputImg = new ASVLOFFSCREEN();
		inputImg.u32PixelArrayFormat = yuv_format;
		inputImg.i32Width = yuv_width;
		inputImg.i32Height = yuv_height;
		if (ASVL_COLOR_FORMAT.ASVL_PAF_I420 == inputImg.u32PixelArrayFormat) {
			inputImg.pi32Pitch[0] = inputImg.i32Width;
			inputImg.pi32Pitch[1] = inputImg.i32Width / 2;
			inputImg.pi32Pitch[2] = inputImg.i32Width / 2;
			yuv_rawdata_size = inputImg.i32Width * inputImg.i32Height * 3 / 2;
		} else if (ASVL_COLOR_FORMAT.ASVL_PAF_NV12 == inputImg.u32PixelArrayFormat) {
			inputImg.pi32Pitch[0] = inputImg.i32Width;
			inputImg.pi32Pitch[1] = inputImg.i32Width;
			yuv_rawdata_size = inputImg.i32Width * inputImg.i32Height * 3 / 2;
		} else if (ASVL_COLOR_FORMAT.ASVL_PAF_NV21 == inputImg.u32PixelArrayFormat) {
			inputImg.pi32Pitch[0] = inputImg.i32Width;
			inputImg.pi32Pitch[1] = inputImg.i32Width;
			yuv_rawdata_size = inputImg.i32Width * inputImg.i32Height * 3 / 2;
		} else if (ASVL_COLOR_FORMAT.ASVL_PAF_YUYV == inputImg.u32PixelArrayFormat) {
			inputImg.pi32Pitch[0] = inputImg.i32Width * 2;
			yuv_rawdata_size = inputImg.i32Width * inputImg.i32Height * 2;
		} else if (ASVL_COLOR_FORMAT.ASVL_PAF_RGB24_B8G8R8 == inputImg.u32PixelArrayFormat) {
			inputImg.pi32Pitch[0] = inputImg.i32Width * 3;
			yuv_rawdata_size = inputImg.i32Width * inputImg.i32Height * 3;
		} else {
			System.out.println("unsupported  yuv format");
			System.exit(0);
		}

		// load YUV Image Data from File
		byte[] imagedata = new byte[yuv_rawdata_size];
		File f = new File(yuv_filePath);
		InputStream ios = null;
		try {
			ios = new FileInputStream(f);
			ios.read(imagedata, 0, yuv_rawdata_size);

		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("error in loading yuv file");
			System.exit(0);
		} finally {
			try {
				if (ios != null) {
					ios.close();
				}
			} catch (IOException e) {
			}
		}

		if (ASVL_COLOR_FORMAT.ASVL_PAF_I420 == inputImg.u32PixelArrayFormat) {
			inputImg.ppu8Plane[0] = new Memory(inputImg.pi32Pitch[0] * inputImg.i32Height);
			inputImg.ppu8Plane[0].write(0, imagedata, 0, inputImg.pi32Pitch[0] * inputImg.i32Height);
			inputImg.ppu8Plane[1] = new Memory(inputImg.pi32Pitch[1] * inputImg.i32Height / 2);
			inputImg.ppu8Plane[1].write(0, imagedata, inputImg.pi32Pitch[0] * inputImg.i32Height,
					inputImg.pi32Pitch[1] * inputImg.i32Height / 2);
			inputImg.ppu8Plane[2] = new Memory(inputImg.pi32Pitch[2] * inputImg.i32Height / 2);
			inputImg.ppu8Plane[2].write(0, imagedata,
					inputImg.pi32Pitch[0] * inputImg.i32Height + inputImg.pi32Pitch[1] * inputImg.i32Height / 2,
					inputImg.pi32Pitch[2] * inputImg.i32Height / 2);
			inputImg.ppu8Plane[3] = Pointer.NULL;
		} else if (ASVL_COLOR_FORMAT.ASVL_PAF_NV12 == inputImg.u32PixelArrayFormat) {
			inputImg.ppu8Plane[0] = new Memory(inputImg.pi32Pitch[0] * inputImg.i32Height);
			inputImg.ppu8Plane[0].write(0, imagedata, 0, inputImg.pi32Pitch[0] * inputImg.i32Height);
			inputImg.ppu8Plane[1] = new Memory(inputImg.pi32Pitch[1] * inputImg.i32Height / 2);
			inputImg.ppu8Plane[1].write(0, imagedata, inputImg.pi32Pitch[0] * inputImg.i32Height,
					inputImg.pi32Pitch[1] * inputImg.i32Height / 2);
			inputImg.ppu8Plane[2] = Pointer.NULL;
			inputImg.ppu8Plane[3] = Pointer.NULL;
		} else if (ASVL_COLOR_FORMAT.ASVL_PAF_NV21 == inputImg.u32PixelArrayFormat) {
			inputImg.ppu8Plane[0] = new Memory(inputImg.pi32Pitch[0] * inputImg.i32Height);
			inputImg.ppu8Plane[0].write(0, imagedata, 0, inputImg.pi32Pitch[0] * inputImg.i32Height);
			inputImg.ppu8Plane[1] = new Memory(inputImg.pi32Pitch[1] * inputImg.i32Height / 2);
			inputImg.ppu8Plane[1].write(0, imagedata, inputImg.pi32Pitch[0] * inputImg.i32Height,
					inputImg.pi32Pitch[1] * inputImg.i32Height / 2);
			inputImg.ppu8Plane[2] = Pointer.NULL;
			inputImg.ppu8Plane[3] = Pointer.NULL;
		} else if (ASVL_COLOR_FORMAT.ASVL_PAF_YUYV == inputImg.u32PixelArrayFormat) {
			inputImg.ppu8Plane[0] = new Memory(inputImg.pi32Pitch[0] * inputImg.i32Height);
			inputImg.ppu8Plane[0].write(0, imagedata, 0, inputImg.pi32Pitch[0] * inputImg.i32Height);
			inputImg.ppu8Plane[1] = Pointer.NULL;
			inputImg.ppu8Plane[2] = Pointer.NULL;
			inputImg.ppu8Plane[3] = Pointer.NULL;
		} else if (ASVL_COLOR_FORMAT.ASVL_PAF_RGB24_B8G8R8 == inputImg.u32PixelArrayFormat) {
			inputImg.ppu8Plane[0] = new Memory(imagedata.length);
			inputImg.ppu8Plane[0].write(0, imagedata, 0, imagedata.length);
			inputImg.ppu8Plane[1] = Pointer.NULL;
			inputImg.ppu8Plane[2] = Pointer.NULL;
			inputImg.ppu8Plane[3] = Pointer.NULL;
		} else {
			System.out.println("unsupported yuv format");
			System.exit(0);
		}

		inputImg.setAutoRead(false);
		return inputImg;
	}

	public static ASVLOFFSCREEN loadImage(InputStream inputStream) {
		ASVLOFFSCREEN inputImg = new ASVLOFFSCREEN();

		if (bUseBGRToEngine) {
			BufferInfo bufferInfo = ImageLoader.getBGRFromInputStream(inputStream);
			inputImg.u32PixelArrayFormat = ASVL_COLOR_FORMAT.ASVL_PAF_RGB24_B8G8R8;
			inputImg.i32Width = bufferInfo.width;
			inputImg.i32Height = bufferInfo.height;
			inputImg.pi32Pitch[0] = inputImg.i32Width * 3;
			inputImg.ppu8Plane[0] = new Memory(inputImg.pi32Pitch[0] * inputImg.i32Height);
			inputImg.ppu8Plane[0].write(0, bufferInfo.buffer, 0, inputImg.pi32Pitch[0] * inputImg.i32Height);
			inputImg.ppu8Plane[1] = Pointer.NULL;
			inputImg.ppu8Plane[2] = Pointer.NULL;
			inputImg.ppu8Plane[3] = Pointer.NULL;
		} else {
			BufferInfo bufferInfo = ImageLoader.getI420FromInputStream(inputStream);
			inputImg.u32PixelArrayFormat = ASVL_COLOR_FORMAT.ASVL_PAF_I420;
			inputImg.i32Width = bufferInfo.width;
			inputImg.i32Height = bufferInfo.height;
			inputImg.pi32Pitch[0] = inputImg.i32Width;
			inputImg.pi32Pitch[1] = inputImg.i32Width / 2;
			inputImg.pi32Pitch[2] = inputImg.i32Width / 2;
			inputImg.ppu8Plane[0] = new Memory(inputImg.pi32Pitch[0] * inputImg.i32Height);
			inputImg.ppu8Plane[0].write(0, bufferInfo.buffer, 0, inputImg.pi32Pitch[0] * inputImg.i32Height);
			inputImg.ppu8Plane[1] = new Memory(inputImg.pi32Pitch[1] * inputImg.i32Height / 2);
			inputImg.ppu8Plane[1].write(0, bufferInfo.buffer, inputImg.pi32Pitch[0] * inputImg.i32Height,
					inputImg.pi32Pitch[1] * inputImg.i32Height / 2);
			inputImg.ppu8Plane[2] = new Memory(inputImg.pi32Pitch[2] * inputImg.i32Height / 2);
			inputImg.ppu8Plane[2].write(0, bufferInfo.buffer,
					inputImg.pi32Pitch[0] * inputImg.i32Height + inputImg.pi32Pitch[1] * inputImg.i32Height / 2,
					inputImg.pi32Pitch[2] * inputImg.i32Height / 2);
			inputImg.ppu8Plane[3] = Pointer.NULL;
		}

		inputImg.setAutoRead(false);
		return inputImg;
	}

	public static ASVLOFFSCREEN loadImage(String filePath) {
		ASVLOFFSCREEN inputImg = new ASVLOFFSCREEN();

		if (bUseBGRToEngine) {
			BufferInfo bufferInfo = ImageLoader.getBGRFromFile(filePath);
			inputImg.u32PixelArrayFormat = ASVL_COLOR_FORMAT.ASVL_PAF_RGB24_B8G8R8;
			inputImg.i32Width = bufferInfo.width;
			inputImg.i32Height = bufferInfo.height;
			inputImg.pi32Pitch[0] = inputImg.i32Width * 3;
			inputImg.ppu8Plane[0] = new Memory(inputImg.pi32Pitch[0] * inputImg.i32Height);
			inputImg.ppu8Plane[0].write(0, bufferInfo.buffer, 0, inputImg.pi32Pitch[0] * inputImg.i32Height);
			inputImg.ppu8Plane[1] = Pointer.NULL;
			inputImg.ppu8Plane[2] = Pointer.NULL;
			inputImg.ppu8Plane[3] = Pointer.NULL;
		} else {
			BufferInfo bufferInfo = ImageLoader.getI420FromFile(filePath);
			inputImg.u32PixelArrayFormat = ASVL_COLOR_FORMAT.ASVL_PAF_I420;
			inputImg.i32Width = bufferInfo.width;
			inputImg.i32Height = bufferInfo.height;
			inputImg.pi32Pitch[0] = inputImg.i32Width;
			inputImg.pi32Pitch[1] = inputImg.i32Width / 2;
			inputImg.pi32Pitch[2] = inputImg.i32Width / 2;
			inputImg.ppu8Plane[0] = new Memory(inputImg.pi32Pitch[0] * inputImg.i32Height);
			inputImg.ppu8Plane[0].write(0, bufferInfo.buffer, 0, inputImg.pi32Pitch[0] * inputImg.i32Height);
			inputImg.ppu8Plane[1] = new Memory(inputImg.pi32Pitch[1] * inputImg.i32Height / 2);
			inputImg.ppu8Plane[1].write(0, bufferInfo.buffer, inputImg.pi32Pitch[0] * inputImg.i32Height,
					inputImg.pi32Pitch[1] * inputImg.i32Height / 2);
			inputImg.ppu8Plane[2] = new Memory(inputImg.pi32Pitch[2] * inputImg.i32Height / 2);
			inputImg.ppu8Plane[2].write(0, bufferInfo.buffer,
					inputImg.pi32Pitch[0] * inputImg.i32Height + inputImg.pi32Pitch[1] * inputImg.i32Height / 2,
					inputImg.pi32Pitch[2] * inputImg.i32Height / 2);
			inputImg.ppu8Plane[3] = Pointer.NULL;
		}

		inputImg.setAutoRead(false);
		return inputImg;
	}

}
