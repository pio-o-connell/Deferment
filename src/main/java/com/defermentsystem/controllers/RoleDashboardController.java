package com.defermentsystem.controllers;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.defermentsystem.domain.Deferral;
import com.defermentsystem.service.DeferralService;

@Controller
public class RoleDashboardController {

	@Autowired
	private DeferralService deferralService;

	@RequestMapping(value = "/student/dashboard", method = RequestMethod.GET)
	public String studentDashboard() {
		return "studentDashboard";
	}

	@RequestMapping(value = "/student/applications", method = RequestMethod.GET)
	public String studentApplications(Principal principal, ModelMap model) {
		requirePrincipal(principal);
		List<Deferral> deferrals = deferralService.listDeferralsByStudent(principal.getName());
		model.addAttribute("deferrals", deferrals);
		return "studentApplications";
	}

	@RequestMapping(value = "/lecturer/dashboard", method = RequestMethod.GET)
	public String lecturerDashboard() {
		return "lecturerDashboard";
	}

	@RequestMapping(value = "/lecturer/pending", method = RequestMethod.GET)
	public String pendingDeferrals(Principal principal, ModelMap model) {
		requirePrincipal(principal);
		model.addAttribute("deferrals",
				deferralService.listUnapprovedDeferralsByCoordinatorId(principal.getName()));
		return "lecturerDeferrals";
	}

	@RequestMapping(value = "/lecturer/processed", method = RequestMethod.GET)
	public String processedDeferrals(Principal principal, ModelMap model) {
		requirePrincipal(principal);
		List<Deferral> processed = new ArrayList<Deferral>();
		for (Deferral deferral : deferralService.listDeferralsByCoordinatorId(principal.getName())) {
			if (!"False".equalsIgnoreCase(deferral.getApproval())) {
				processed.add(deferral);
			}
		}
		model.addAttribute("deferrals", processed);
		return "lecturerDeferrals";
	}

	@RequestMapping(value = "/admin/dashboard", method = RequestMethod.GET)
	public String administratorDashboard() {
		return "administratorDashboard";
	}

	private void requirePrincipal(Principal principal) {
		if (principal == null || principal.getName() == null) {
			throw new AccessDeniedException("An authenticated account is required.");
		}
	}
}
