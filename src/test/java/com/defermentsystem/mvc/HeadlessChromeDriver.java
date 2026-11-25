package com.defermentsystem.mvc;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

final class HeadlessChromeDriver {

	private HeadlessChromeDriver() {
	}

	// Keep a strong reference: java.util.logging holds loggers weakly and would drop the level
	private static final Logger CDP_LOGGER = Logger.getLogger("org.openqa.selenium.chromium.ChromiumDriver");
	private static final Logger CDP_FINDER_LOGGER = Logger.getLogger("org.openqa.selenium.devtools.CdpVersionFinder");

	static WebDriver create() {
		CDP_LOGGER.setLevel(Level.SEVERE);
		CDP_FINDER_LOGGER.setLevel(Level.SEVERE);
		ChromeOptions options = new ChromeOptions();
		options.addArguments("--headless", "--disable-gpu", "--window-size=1920,1080");
		return new ChromeDriver(options);
	}
}
