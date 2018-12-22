package com.bdca.face;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.bdca.face.service.FaceService;

@RunWith(SpringRunner.class)
@SpringBootTest
public class FaceServiceTests {

	@Autowired
	FaceService faceService;

	@Test
	public void comparison() {
//		faceService.comparison("1-2.jpg", "1-2.jpg");
	}

	public static void main(String args[]) {
		for (int i = 0; i < 100; i++) {
			System.out.printf("%d\t%f\n", i, 1 / (1 + Math.pow(Math.E, -i)));
		}
	}
}
