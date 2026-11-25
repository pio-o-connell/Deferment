package com.defermentsystem.mvc;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.github.springtestdbunit.annotation.DatabaseOperation;
import com.github.springtestdbunit.annotation.DatabaseSetup;
import com.defermentsystem.domain.Deferral;
import com.defermentsystem.repository.DeferralDAO;



// TODO: Auto-generated Javadoc

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration({ "classpath:repository-context.xml" })
@TestExecutionListeners({ DependencyInjectionTestExecutionListener.class,
		DbUnitTestExecutionListener.class })
public class DeferralTest {
	
	/** The DeferralDAO. */
	@Autowired
	DeferralDAO deferralDAO;
	
	/** The driver. */
	private WebDriver driver;
	
	/** The base url. */
	private String baseUrl;
	
	/** The verification errors. */
	private StringBuffer verificationErrors = new StringBuffer();

	 
  	@Before
	  public void setUp() throws Exception {
	    driver = HeadlessChromeDriver.create();
	    baseUrl = "http://localhost:8080/";
	    driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
	  }
	
	 
  	@Test
	  @DatabaseSetup(value = "classpath:databaseEntries.xml", type = DatabaseOperation.CLEAN_INSERT)
	  public void testCreateDeferral() throws Exception {
		
	    driver.get(baseUrl + "/SpringWebProject/home");
	    driver.findElement(By.linkText("Download Deferral Form")).click();
	    
	    driver.findElement(By.linkText("Menu Options")).click();
	    driver.findElement(By.partialLinkText("Apply for")).click();
	    driver.findElement(By.linkText("Deferral by Module")).click();
	    driver.findElement(By.id("studentId")).clear();
	    driver.findElement(By.id("studentId")).sendKeys("R0004444");
	    driver.findElement(By.cssSelector("input[type=\"submit\"]")).click();

	    WebElement elem = driver.findElement(By.cssSelector("input[type='file']"));
	    elem.sendKeys(new File("src/main/webapp/resources/form/Withdrawal_Form.pdf").getAbsolutePath());
	    
	    new Select(driver.findElement(By.id("moduleId"))).selectByVisibleText("Cloud Application Frameworks");
	    new Select(driver.findElement(By.id("programmeId"))).selectByVisibleText("24248");
	    
	    //getting Selenium to properly click on the "Save" button.Neither sendKeys() or type() was working
	    WebElement element = driver.findElement(By.xpath("/html/body/div[4]/div[2]/div/form/div/div[4]/div[2]/input"));
	    ((JavascriptExecutor)driver).executeScript("arguments[0].click();", element); 
	      
	    //instructing Selenium to wait for following page to load by searching for element on next page
	    WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(40));
	    WebElement element2 = wait.until(ExpectedConditions.elementToBeClickable(By.id("myTable")));
	   
	    int rowCount = deferralDAO.countRows();
		assertEquals(3, rowCount);

		// Confirm correct Module Codes;
		List<Deferral> studentDeferrals = deferralDAO.listDeferralsByStudent("R0004444");
		assertEquals("COMP8040", studentDeferrals.get(0).getModuleId());
		assertEquals("KCMSD_Y5",studentDeferrals.get(0).getProgrammeId());
	  }
	
	

  	@Test
	  @DatabaseSetup(value = "classpath:databaseEntries.xml", type = DatabaseOperation.CLEAN_INSERT)
	  public void testCreateDeferralProg() throws Exception {
	    driver.get(baseUrl + "/SpringWebProject/home");
	    driver.findElement(By.linkText("Download Deferral Form")).click();
	    driver.findElement(By.linkText("Menu Options")).click();
	    driver.findElement(By.partialLinkText("Apply for")).click();
	    driver.findElement(By.linkText("Deferral by Programme")).click();
	    driver.findElement(By.id("studentId")).clear();
	    driver.findElement(By.id("studentId")).sendKeys("R0004444");
	    driver.findElement(By.cssSelector("input[type=\"submit\"]")).click();
	    
	    WebElement elem = driver.findElement(By.cssSelector("input[type='file']"));
	    elem.sendKeys(new File("src/main/webapp/resources/form/Withdrawal_Form.pdf").getAbsolutePath());
	    
	    new Select(driver.findElement(By.id("programmeId"))).selectByVisibleText("KCMSD_Y5");
	    
	    WebElement element = driver.findElement(By.xpath("/html/body/div[4]/div[2]/div/form/div/div[3]/div[2]/input"));
	    ((JavascriptExecutor)driver).executeScript("arguments[0].click();", element); 
	    
	    WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(40));
	    WebElement element2 = wait.until(ExpectedConditions.elementToBeClickable(By.id("myTable")));
	    
	    int rowCount = deferralDAO.countRows();
		assertEquals(3, rowCount);
	  }
	  
	
   	@After
	   public void tearDown() throws Exception {
	    if (driver != null) {
	      driver.quit();
	    }
	    String verificationErrorString = verificationErrors.toString();
	    if (!"".equals(verificationErrorString)) {
	      fail(verificationErrorString);
	    }
	    
	  }

	
}

