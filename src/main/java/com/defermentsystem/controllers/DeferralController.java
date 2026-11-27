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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
	private static final long MAX_DOCUMENT_SIZE = 5L * 1024L * 1024L;

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
	public String addModuleDeferralPage1(Principal principal, ModelMap model) {
		requireStudent(principal, null);
		List<Module> listModules = moduleService.listModules();
		model.addAttribute("moduleList",listModules );
		Deferral deferral = new Deferral();
		deferral.setStudentId(principal.getName());
		model.addAttribute("deferral", deferral);
		return "newModuleDeferralPage1";
	}
	
	/* Once the user enters their student Id, this method will display 
	 * the student's details and their corresponding modules.
	*/	
	
	@RequestMapping(value = "/addModuleDeferralPage1", method = RequestMethod.POST)
	public String displayModuleDeferralPage1(
			@ModelAttribute("deferral") @Valid Deferral deferral, BindingResult result,
			Principal principal, ModelMap model) {
		requireStudent(principal, deferral.getStudentId());

		if (result.hasErrors()) {
			return "newModuleDeferralPage1";
		}
		
		//Get the student's FirstName,LastName and Registered Modules
		try{
		String studentId=principal.getName();
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
			BindingResult result, Principal principal, ModelMap model) {
		requireStudent(principal, deferral.getStudentId());
		if (result.hasErrors()) {
			model.addAttribute("message", "Creation of deferral failed. Check the submitted information.");
			return "displayError";
		}

		Module module;
		Student student;
		int crnNumber;
		try {
			byte[] document = validateImage(file);
			crnNumber = Integer.parseInt(deferral.getProgrammeId());
			module = moduleService.getModuleByName(deferral.getModuleId(), crnNumber);
			student = studentService.getStudent(deferral.getStudentId());
			deferralService.submitModuleDeferral(deferral.getStudentId(), module.getModuleId(),
					crnNumber, document, uploadDirectory());
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

	private byte[] validateImage(MultipartFile image) throws IOException {
		if (image == null || image.isEmpty() || image.getSize() > MAX_DOCUMENT_SIZE) {
			throw new FormUploadException("A PDF document up to 5 MB is required.");
		}
		byte[] document = image.getBytes();
		if (document.length < 10 || document[0] != '%' || document[1] != 'P'
				|| document[2] != 'D' || document[3] != 'F' || document[4] != '-') {
			throw new FormUploadException("The uploaded content is not a PDF document.");
		}
		int eofStart = Math.max(0, document.length - 1024);
		boolean hasEndMarker = false;
		for (int i = eofStart; i <= document.length - 5; i++) {
			if (document[i] == '%' && document[i + 1] == '%' && document[i + 2] == 'E'
					&& document[i + 3] == 'O' && document[i + 4] == 'F') {
				hasEndMarker = true;
				break;
			}
		}
		if (!hasEndMarker) {
			throw new FormUploadException("The uploaded PDF is incomplete.");
		}
		return document;
	}

	private Path uploadDirectory() throws IOException {
		String root = servletContext.getRealPath("/");
		if (root == null) {
			throw new IOException("The deployed application has no filesystem path for uploaded forms.");
		}
		return Paths.get(root, "WEB-INF", "uploadedForms");
	}
	

	
	/*This method will display a View for the user to enter his/her student Id*/
	
	@RequestMapping(value = "/addProgrammeDeferralPage1", method = RequestMethod.GET)
	public String addProgrammeDeferralPage1(Principal principal, ModelMap model) {
		requireStudent(principal, null);
		List<Programme> listProgrammes = programmeService.listProgrammes();
		model.addAttribute("programmeList",listProgrammes );
		Deferral deferral = new Deferral();
		deferral.setStudentId(principal.getName());
		model.addAttribute("deferral", deferral);
		return "newProgrammeDeferralPage1";
	}
	
	/* Once the user enters their student Id, this method will display 
	 * the student's details and their corresponding Programme.
	*/	
	
	@RequestMapping(value = "/addProgrammeDeferralPage1", method = RequestMethod.POST)
	public String displayProgrammeDeferralPage1(
			@ModelAttribute("deferral") @Valid Deferral deferral, BindingResult result,
			Principal principal, ModelMap model) {
		requireStudent(principal, deferral.getStudentId());

		if (result.hasErrors()) {
			return "newProgrammeDeferralPage1";
		}
		
		//Get the student's FirstName,LastName and Registered Programme
		try{
		String studentId=principal.getName();
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
			BindingResult result, Principal principal, ModelMap model) {
		requireStudent(principal, deferral.getStudentId());

		if (result.hasErrors()) {
			model.addAttribute("message", "Creation of deferral failed. Check the submitted information.");
			return "displayError";
		}
		Student student;
		try {
			byte[] document = validateImage(file);
			student = studentService.getStudent(deferral.getStudentId());
			deferralService.submitProgrammeDeferral(deferral.getStudentId(), deferral.getProgrammeId(),
					document, uploadDirectory());
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
	

	@RequestMapping(value= "/delete/id/{id}", method = RequestMethod.POST)
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
	public String modifyDeferral(@PathVariable int id, Principal principal, ModelMap model) {
		Deferral deferralModify=deferralService.getDeferral(id);
		assertCanAccessDeferral(principal, deferralModify);
		List<Module> listModules = moduleService.listModules();
		model.addAttribute("moduleList",listModules );
		model.addAttribute("message", "Deferal with id "+ id +" can now be modified");
		model.addAttribute("deferral", deferralModify);
		return "modifyDefForm";	} 
	
	@RequestMapping(value="/modify/id/{id}/decision", method = RequestMethod.POST)
	public String decideDeferral(@PathVariable int id, @RequestParam("approval") String approval,
			@RequestParam(value="rejectionReason", required=false) String rejectionReason,
			Principal principal, ModelMap model) {
		if (principal == null) {
			throw new AccessDeniedException("An authenticated lecturer or administrator is required.");
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
			Principal principal = request.getUserPrincipal();
			Deferral deferral = deferralService.getDeferral(id);
			assertCanAccessDeferral(principal, deferral);
			String applicationRoot = servletContext.getRealPath("/");
			if (applicationRoot == null) {
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			return;
			}
			File uploadDirectory = new File(applicationRoot, "WEB-INF/uploadedForms").getCanonicalFile();
			File downloadFile = new File(uploadDirectory, id + ".pdf").getCanonicalFile();
			if (!downloadFile.toPath().startsWith(uploadDirectory.toPath()) || !downloadFile.isFile()) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
			}
			response.setContentType("application/pdf");
			response.setContentLength((int) downloadFile.length());
			response.setHeader("Content-Disposition",
				String.format("attachment; filename=\"%s\"", downloadFile.getName()));
			response.setHeader("X-Content-Type-Options", "nosniff");
			response.setHeader("Cache-Control", "private, no-store");
			try (FileInputStream inputStream = new FileInputStream(downloadFile);
				OutputStream outStream = response.getOutputStream()) {
			byte[] buffer = new byte[8192];
			int bytesRead;
			while ((bytesRead = inputStream.read(buffer)) != -1) {
				outStream.write(buffer, 0, bytesRead);
			}
			outStream.flush();
			}
		} catch (FileNotFoundException e) {
			try {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			} catch (IOException responseError) {
			logger.error("Could not report a missing supporting document", responseError);
			}
		} catch (IOException e) {
			logger.error("Could not read the requested supporting document", e);
			if (!response.isCommitted()) {
			try {
				response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			} catch (IOException responseError) {
				logger.error("Could not report a supporting-document download failure", responseError);
			}
			}
		}
	} 

	private void requireStudent(Principal principal, String submittedStudentId) {
		if (principal == null || principal.getName() == null
				|| (submittedStudentId != null && !principal.getName().equals(submittedStudentId))) {
			throw new AccessDeniedException("Students may submit or view only their own deferrals.");
		}
	}

	private void assertCanAccessDeferral(Principal principal, Deferral deferral) {
		if (principal == null || deferral == null) {
			throw new AccessDeniedException("An authenticated user with permission is required.");
		}
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (hasAuthority(authentication, "ROLE_ADMIN")) {
			return;
		}
		if (hasAuthority(authentication, "ROLE_LECTURER")
				&& principal.getName().equals(deferral.getLectId())) {
			return;
		}
		if (hasAuthority(authentication, "ROLE_USER")
				&& principal.getName().equals(deferral.getStudentId())) {
			return;
		}
		throw new AccessDeniedException("You are not authorised to access this deferral.");
	}

	private boolean hasAuthority(Authentication authentication, String authority) {
		if (authentication == null || authentication.getAuthorities() == null) {
			return false;
		}
		for (org.springframework.security.core.GrantedAuthority granted : authentication.getAuthorities()) {
			if (authority.equals(granted.getAuthority())) {
				return true;
			}
		}
		return false;
	}
	
	
}
