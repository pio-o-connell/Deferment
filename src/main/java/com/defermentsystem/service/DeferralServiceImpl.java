package com.defermentsystem.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.defermentsystem.domain.Deferral;
import com.defermentsystem.exceptions.InvalidDeferralStateException;
import com.defermentsystem.repository.DeferralDAO;

@Service
@Transactional(readOnly = true)
public class DeferralServiceImpl implements DeferralService {

	@Autowired
	private DeferralDAO deferralDAO;
	
	@Override
	public void setDeferralDAO(DeferralDAO deferralDAO) {
		this.deferralDAO = deferralDAO;

	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public int createDeferralGetId(String studentID, String lectID,
			String progID, String moduleID) {
		return deferralDAO.createDeferralGetId(studentID, lectID, progID, moduleID);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void createModuleDeferral(String studentId, String moduleId,
			int crnNumber) {
		deferralDAO.createModuleDeferral(studentId, moduleId, crnNumber);

	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void createProgrammeDeferral(String studentId, String programmeId) {
		deferralDAO.createProgrammeDeferral(studentId, programmeId);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void deleteDeferralById(int id) {
		deferralDAO.deleteDeferralById(id);

	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void deleteDeferralsByStudentID(String studentId) {
		deferralDAO.deleteDeferralsByStudentID(studentId);

	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void deleteDeferralsByModuleID(String moduleId) {
		deferralDAO.deleteDeferralsByModuleID(moduleId);
	}

	@Override
	public Deferral getDeferral(int id) {
		return deferralDAO.getDeferral(id);
	}

	@Override
	public List<Deferral> listDeferrals() {
		return deferralDAO.listDeferrals();
	}

	@Override
	public List<Deferral> listDeferralsByStudent(String studentID) {
		return deferralDAO.listDeferralsByStudent(studentID);
	}

	@Override
	public List<Deferral> listDeferralsByProgramme(String programmeID) {
		return deferralDAO.listDeferralsByProgramme(programmeID);
	}

	@Override
	public List<Deferral> listUnapprovedDeferrals(String unapproved) {
		return deferralDAO.listUnapprovedDeferrals(unapproved);
	}

	@Override
	public List<Deferral> listUnapprovedDeferralsByProgramme(String unapproved,
			String programmeID) {
		return deferralDAO.listUnapprovedDeferralsByProgramme(unapproved, programmeID);
	}

	@Override
	public List<Deferral> listUnapprovedDeferralsByProgrammeCoordinatorName(
			String unapproved, String firstname) {
		return deferralDAO.listUnapprovedDeferralsByProgrammeCoordinatorName(unapproved, firstname);
	}

	@Override
	public List<Deferral> listApprovedDeferralsByStudent(String approved,
			String studentID) {
		return deferralDAO.listApprovedDeferralsByStudent(approved, studentID);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void approveModuleDeferral(String studId, String moduleId) {
		int updated = deferralDAO.approveModuleDeferral(studId, moduleId);
		if (updated == 0) {
			throw new InvalidDeferralStateException("No pending module deferral is available for approval.");
		}

	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void approveProgrammeDeferral(String studId, String programmeId) {
		int updated = deferralDAO.approveProgrammeDeferral(studId, programmeId);
		if (updated == 0) {
			throw new InvalidDeferralStateException("No pending programme deferral is available for approval.");
		}

	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void updateDeferral(int id, String approval) {
		deferralDAO.updateDeferral(id, approval);

	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void decideDeferral(int id, String lecturerId, String decision) {
		if (!"True".equalsIgnoreCase(decision) && !"Rejected".equalsIgnoreCase(decision)) {
			throw new InvalidDeferralStateException("A deferral can only be approved or rejected.");
		}

		Deferral deferral = deferralDAO.getDeferral(id);
		if (deferral == null) {
			throw new InvalidDeferralStateException("Deferral does not exist.");
		}
		if (!"False".equalsIgnoreCase(deferral.getApproval())) {
			throw new InvalidDeferralStateException("Deferral is no longer pending.");
		}
		if (lecturerId != null && !lecturerId.equals(deferral.getLectId())) {
			throw new InvalidDeferralStateException("Lecturer is not authorised to decide this deferral.");
		}

		String normalisedDecision = "True".equalsIgnoreCase(decision) ? "True" : "Rejected";
		int updated = deferralDAO.updateDeferralStatusIfPending(id, normalisedDecision, deferral.getLectId());
		if (updated != 1) {
			throw new InvalidDeferralStateException("Deferral changed before the decision could be recorded.");
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public int createModuleDeferralGetId(String studentId, String moduleId,
			int crnNumber) {
		return deferralDAO.createModuleDeferralGetId(studentId, moduleId, crnNumber);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public ArrayList<Integer> createProgrammeDeferralGetId(String studentId,
			String programmeId) {
		return deferralDAO.createProgrammeDeferralGetId(studentId, programmeId);
	}

	@Override
	public List<Deferral> listDeferralsByModule(String moduleID) {
		
		return deferralDAO.listDeferralsByModule(moduleID);
	}

	@Override
	public List<Deferral> listDeferralsByCoordinatorId(String coordinatorId) {
		return deferralDAO.listDeferralsByCoordinatorId(coordinatorId);
	}

	@Override
	public List<Deferral> listUnapprovedDeferralsByCoordinatorId(
			String coordinatorId) {
		return deferralDAO.listUnapprovedDeferralsByCoordinatorId(coordinatorId);
	}

}

