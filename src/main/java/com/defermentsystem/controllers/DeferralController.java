package com.defermentsystem.controllers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Principal;
import java.util.Date;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.mail.MailException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.defermentsystem.domain.Deferral;
import com.defermentsystem.domain.Mail;
import com.defermentsystem.domain.Module;
import com.defermentsystem.domain.Programme;
import com.defermentsystem.domain.Student;
import com.defermentsystem.service.DeferralService;
import com.defermentsystem.service.ModuleService;
import com.defermentsystem.service.ProgrammeService;
import com.defermentsystem.service.StudentService;
import com.defermentsystem.exceptions.FormUploadException;
import com.defermentsystem.exceptions.InvalidDeferralStateException;


@Controller
@RequestMapping("/admin/deferral")
public class DeferralController {

	private static final Logger logger = Logger.getLogger(DeferralController.class);

	@Autowired
	DeferralService deferralService;
	@Autowired
	ModuleService moduleService;
	@Autowired
	ProgrammeService programmeService;
	@Autowired
	StudentService studentService;
	@Autowired
	private ServletContext servletContext;
	@Autowired
	private Mail mail;
	
	
	
	@RequestMapping(value = "/listall", method = RequestMethod.GET)
	public String listAll(ModelMap model) {
		Date date = new java.util.Date();
		List<Deferral> listDefferals = deferralService.listDeferrals();
		model.addAttribute("deferrals", listDefferals);
		model.addAttribute("message", "List of deferrals");
		model.addAttribute("now", date);
		return "displayDeferrals";
	}
	
	
	@RequestMapping(value = "/list/id/{programmeId}", method = RequestMethod.GET)
	public String listDeferralsByProgrammeId(@PathVariable String programmeId, ModelMap model) {
		List<Deferral> listDeferrals = deferralService.listDeferralsByProgramme(programmeId);
		model.addAttribute("deferrals", listDeferrals);
		return "displayDeferrals";
	}
	
	
	@RequestMapping(value = "/list/moduleId/{moduleId}", method = RequestMethod.GET)
	public String listDeferralsByModuleId(@PathVariable String moduleId, ModelMap model) {
		List<Deferral> listDeferrals = deferralService.listDeferralsByModule(moduleId);
		model.addAttribute("deferrals", listDeferrals);
		return "displayDeferrals";
	}
	
	
	@RequestMapping(value = "/list/coordinatorId/{coordinatorId}", method = RequestMethod.GET)
	public String listDeferralsByCoordinatorId(@PathVariable String coordinatorId, ModelMap model){
		List<Deferral> listDeferrals = deferralService.listDeferralsByCoordinatorId(coordinatorId);
		model.addAttribute("deferrals", listDeferrals);
		model.addAttribute("message", "All deferrals for "+ coordinatorId);
		return "displayDeferrals";
	}
	
	
	 
	@RequestMapping(value = "/list/unapproved/coordinatorId/{coordinatorId}", method = RequestMethod.GET)
	public String listUnapprovedDeferralsByCoordinatorId(@PathVariable String coordinatorId, ModelMap model){
		List<Deferral> listDeferrals = deferralService.listUnapprovedDeferralsByCoordinatorId(coordinatorId);
		model.addAttribute("deferrals", listDeferrals);
		model.addAttribute("message", "All unapproved deferrals for "+ coordinatorId);
		return "displayDeferrals";
	}
	
	

	 
	@RequestMapping(value = "/advancedlist", method = RequestMethod.GET)
	public String advancedSearch() {
		return "displayDeferralsByStudent";
	}
	
	
	@RequestMapping(value = "/advancedlist/studentId/{studentId}", method = RequestMethod.GET)
	public String listDeferralsByStudent(@PathVariable String studentId,
			ModelMap model) {
		Date date = new java.util.Date();
		List<Deferral> listDefferals = deferralService.listDeferralsByStudent(studentId);
		model.addAttribute("deferrals", listDefferals);
		model.addAttribute("now", date);
		return "displayDeferrals";

	}
	
	
	
	
	
	/*This method will display a View for the user to enter his/her student Id*/
	
	@RequestMapping(value = "/addModuleDeferralPage1", method = RequestMethod.GET)
	public String addModuleDeferralPage1(ModelMap model) {
		List<Module> listModules = moduleService.listModules();
		model.addAttribute("moduleList",listModules );
		model.addAttribute("deferral", new Deferral());
		return "newModuleDeferralPage1";
	}
	
	/* Once the user enters their student Id, this method will display 
	 * the student's details and their corresponding modules.
	*/	
	
	@RequestMapping(value = "/addModuleDeferralPage1", method = RequestMethod.POST)
	public String displayModuleDeferralPage1(
			@ModelAttribute("deferral") @Valid Deferral deferral,BindingResult result, ModelMap model) {

		if (result.hasErrors()) {
			return "newModuleDeferralPage1";
		}
		
		//Get the student's FirstName,LastName and Registered Modules
		try{
		String studentId=deferral.getStudentId();
		List<Module> listStudentModules = moduleService.listStudentModules(studentId);
		Student student=studentService.getStudent(studentId);
		model.addAttribute("studentFirstName",student.getFirstName());
		model.addAttribute("studentLastName",student.getLastName());
		model.addAttribute("studentId",studentId);
		model.addAttribute("moduleList",listStudentModules );
		model.addAttribute("deferral", deferral);
		return "newModuleDeferralPage2";
		}
		catch(Exception e)
		{
			model.addAttribute("message", "Student ID could not be found, please try again");
			return "newModuleDeferralPage1";
		}

	}
	
	/* This method will create the deferral entry for the specfic module.
	*/	
	@RequestMapping(value = "/addModuleDeferralPage2", method = RequestMethod.POST)
	public String displayModuleDeferralPage2(
			@ModelAttribute("deferral") Deferral deferral,@RequestParam("file") MultipartFile file,
			BindingResult result, ModelMap model) {
		if (result.hasErrors()) {
			model.addAttribute("message", "Creation of deferral failed. Check the submitted information.");
			return "displayError";
		}

		Module module;
		Student student;
		int crnNumber;
		try {
			validateImage(file);
			crnNumber = Integer.parseInt(deferral.getProgrammeId());
			module = moduleService.getModuleByName(deferral.getModuleId(), crnNumber);
			student = studentService.getStudent(deferral.getStudentId());
			deferralService.submitModuleDeferral(deferral.getStudentId(), module.getModuleId(),
					crnNumber, file.getBytes(), uploadDirectory());
		} catch (FormUploadException | IllegalArgumentException e) {
			model.addAttribute("message", "Creation of deferral failed. Check the selected module and PDF.");
			logger.warn("Module deferral validation failed", e);
			return "displayError";
		} catch (IOException | DataAccessException e) {
			model.addAttribute("message", "Creation of deferral failed. Please try again.");
			logger.error("Module deferral submission failed", e);
			return "displayError";
		}

		try {
			mail.sendModuleDeferralMail(student.getFirstName() + " " + student.getLastName(),
					student.getStudentId(), module.getModuleId(), crnNumber);
		} catch (MailException e) {
			logger.error("Module deferral committed, but the admissions email failed", e);
			model.addAttribute("message", "Deferral saved, but the admissions notification could not be sent.");
		}
		model.addAttribute("deferrals", deferralService.listDeferralsByStudent(deferral.getStudentId()));
		model.addAttribute("now", new Date());
		return "displayDeferrals";
	}

	private void validateImage(MultipartFile image) {
		if (image.isEmpty() || !"application/pdf".equalsIgnoreCase(image.getContentType())) {
			throw new FormUploadException("Only non-empty PDFs are accepted.");
		}
	}

	private Path uploadDirectory() throws IOException {
		String root = servletContext.getRealPath("/");
		if (root == null) {
			throw new IOException("The deployed application has no filesystem path for uploaded forms.");
		}
		return Paths.get(root, "resources", "form", "uploadedForms");
	}
	

	
	/*This method will display a View for the user to enter his/her student Id*/
	
	@RequestMapping(value = "/addProgrammeDeferralPage1", method = RequestMethod.GET)
	public String addProgrammeDeferralPage1(ModelMap model) {
		List<Programme> listProgrammes = programmeService.listProgrammes();
		model.addAttribute("programmeList",listProgrammes );
		model.addAttribute("deferral", new Deferral());
		return "newProgrammeDeferralPage1";
	}
	
	/* Once the user enters their student Id, this method will display 
	 * the student's details and their corresponding Programme.
	*/	
	
	@RequestMapping(value = "/addProgrammeDeferralPage1", method = RequestMethod.POST)
	public String displayProgrammeDeferralPage1(
			@ModelAttribute("deferral")  @Valid  Deferral deferral,BindingResult result, ModelMap model) {

		if (result.hasErrors()) {
			return "newProgrammeDeferralPage1";
		}
		
		//Get the student's FirstName,LastName and Registered Programme
		try{
		String studentId=deferral.getStudentId();
		Student student=studentService.getStudent(studentId);
		model.addAttribute("studentFirstName",student.getFirstName());
		model.addAttribute("studentLastName",student.getLastName());
		model.addAttribute("studentId",studentId);
		model.addAttribute("studentProgramme",programmeService.getStudentProgrammeId(studentId) );
		model.addAttribute("deferral", deferral);
		return "newProgrammeDeferralPage2";
		}
		catch(Exception e)
		{
			model.addAttribute("message", "Student ID could not be found, please try again");
			return "newModuleDeferralPage1";
		}
		

	}
	
	/* This method will create the deferral entry for the specfic programme and 
	 * all the corresponding modules.
	*/
	@RequestMapping(value = "/addProgrammeDeferralPage2", method = RequestMethod.POST)
	public String displayProgrammeDeferralPage2(
			@ModelAttribute("deferral") Deferral deferral,@RequestParam("file") MultipartFile file,
			BindingResult result, ModelMap model) {

		if (result.hasErrors()) {
			model.addAttribute("message", "Creation of deferral failed. Check the submitted information.");
			return "displayError";
		}
		Student student;
		try {
			validateImage(file);
			student = studentService.getStudent(deferral.getStudentId());
			deferralService.submitProgrammeDeferral(deferral.getStudentId(), deferral.getProgrammeId(),
					file.getBytes(), uploadDirectory());
		} catch (FormUploadException | IllegalArgumentException e) {
			model.addAttribute("message", "Creation of deferral failed. Check the selected programme and PDF.");
			logger.warn("Programme deferral validation failed", e);
			return "displayError";
		} catch (IOException | DataAccessException e) {
			model.addAttribute("message", "Creation of deferral failed. Please try again.");
			logger.error("Programme deferral submission failed", e);
			return "displayError";
		}
		try {
			mail.sendProgrammeDeferralMail(student.getFirstName() + " " + student.getLastName(),
					student.getStudentId(), deferral.getProgrammeId());
		} catch (MailException e) {
			logger.error("Programme deferral committed, but the admissions email failed", e);
			model.addAttribute("message", "Deferral saved, but the admissions notification could not be sent.");
		}
		model.addAttribute("deferrals", deferralService.listDeferralsByStudent(deferral.getStudentId()));
		model.addAttribute("now", new Date());
		return "displayDeferrals";
	}
	

	@RequestMapping(value= "/delete/id/{id}", method = RequestMethod.GET)
	public String deleteStudentById(@PathVariable int id, ModelMap model){
		Deferral deferral = deferralService.getDeferral(id);
		deferralService.deleteDeferralById(id);
		model.addAttribute("message", "Deferral "+ id + " has been deleted");
		model.addAttribute("id", id);
		model.addAttribute("studentId", deferral.getStudentId());
		model.addAttribute("lectId", deferral.getLectId());
		model.addAttribute("progId", deferral.getProgrammeId());
		model.addAttribute("moduleId", deferral.getModuleId());
		model.addAttribute("approval", deferral.getApproval());
		return "displayDeferral";
	}
	

	@RequestMapping(value = "/modify/id/{id}", method = RequestMethod.GET) 
	public String modifyDeferral(@PathVariable int id, ModelMap model) { 
		Deferral deferralModify=deferralService.getDeferral(id);
		List<Module> listModules = moduleService.listModules();
		model.addAttribute("moduleList",listModules );
		model.addAttribute("message", "Deferal with id "+ id +" can now be modified");
		model.addAttribute("deferral", deferralModify);
		return "modifyDefForm";	} 
	
	@RequestMapping(value="/modify/id/{id}/decision", method = RequestMethod.POST)
	public String decideDeferral(@PathVariable int id, @RequestParam("approval") String approval,
			@RequestParam(value="rejectionReason", required=false) String rejectionReason,
			Principal principal, ModelMap model) {
		if (principal == null || !DeferralService.DECISION_LECTURER_ID.equals(principal.getName())) {
			throw new AccessDeniedException("Only lecturer L001 may decide deferrals.");
		}
		try {
			deferralService.decideDeferral(id, principal.getName(), approval, rejectionReason);
		} catch (InvalidDeferralStateException e) {
			model.addAttribute("message", "Deferral decision failed: " + e.getMessage());
			model.addAttribute("deferrals", deferralService.listDeferrals());
			return "displayDeferrals";
		} catch (DataAccessException e) {
			logger.error("Deferral decision failed", e);
			model.addAttribute("message", "Deferral decision failed. Please try again.");
			model.addAttribute("deferrals", deferralService.listDeferrals());
			return "displayDeferrals";
		}
		Deferral deferralModify=deferralService.getDeferral(id);
		model.addAttribute("message", "Deferral with id "+ id +" has been modified");
		model.addAttribute("id", id);
		model.addAttribute("studentId", deferralModify.getStudentId());
		model.addAttribute("lectId", deferralModify.getLectId());
		model.addAttribute("progId", deferralModify.getProgrammeId());
		model.addAttribute("moduleId", deferralModify.getModuleId());
		model.addAttribute("approval", approval);
		
		List<Deferral> listDefferals = deferralService.listDeferrals();
		model.addAttribute("deferrals", listDefferals);
		
		return "displayDeferrals";
				
	}
	
	
	
	@RequestMapping(value = "/downloadForm/id/{id}", method = RequestMethod.GET) 
	public @ResponseBody void downloadWithdrawalForm(@PathVariable int id, HttpServletRequest request, HttpServletResponse response) {   
		try {
	 
		// set the path file
		File fullPath = new File(servletContext.getRealPath("/")+"/resources/form/uploadedForms");
        File downloadFile = new File(fullPath+"/"+id + ".pdf");
        FileInputStream inputStream = new FileInputStream(downloadFile);
       
        // get MIME type of the file
        String mimeType = "application/pdf";         
 
        // set content attributes for the response
        response.setContentType(mimeType);
        response.setContentLength((int) downloadFile.length());
 
        // set headers for the response
        String headerKey = "Content-Disposition";
        String headerValue = String.format("attachment; filename=\"%s\"", downloadFile.getName());
        response.setHeader(headerKey, headerValue);
 
        // get output stream of the response
        OutputStream outStream = response.getOutputStream();
 
        byte[] buffer = new byte[1024];
        int bytesRead = -1;
 
        // write bytes read from the input stream into the output stream
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outStream.write(buffer, 0, bytesRead);
        }		   
 
        inputStream.close();
        outStream.close();
        
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} 
	} 
	
	
}
