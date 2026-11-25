package com.defermentsystem.mvc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.openqa.selenium.WebDriver;

public class HeadlessChromeDriverTest {

	@Test
	public void opensPageInHeadlessChrome() {
		WebDriver driver = HeadlessChromeDriver.create();
		try {
			assertTrue(driver.manage().window().getSize().getHeight() >= 1000);
			driver.get("data:text/html,<title>Headless Chrome</title>");
			assertEquals("Headless Chrome", driver.getTitle());
		} finally {
			driver.quit();
		}
	}
}
