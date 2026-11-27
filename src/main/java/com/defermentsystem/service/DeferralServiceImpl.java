package com.defermentsystem.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.defermentsystem.domain.Deferral;
import com.defermentsystem.exceptions.InvalidDeferralStateException;
import com.defermentsystem.repository.DeferralDAO;

@Service
@Transactional(readOnly = true)
public class DeferralServiceImpl implements DeferralService {

	private static final Logger logger = Logger.getLogger(DeferralServiceImpl.class);
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
	public void decideDeferral(int id, String lecturerId, String decision) {
		decideDeferral(id, lecturerId, decision, null);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void decideDeferral(int id, String lecturerId, String decision, String rejectionReason) {
		if (!"True".equalsIgnoreCase(decision) && !"Rejected".equalsIgnoreCase(decision)) {
			throw new InvalidDeferralStateException("A deferral can only be approved or rejected.");
		}

		String reason = rejectionReason == null ? null : rejectionReason.trim();
		if (reason != null && reason.isEmpty()) {
			reason = null;
		}
		if (reason != null && reason.length() > 1000) {
			throw new InvalidDeferralStateException("The rejection reason must not exceed 1000 characters.");
		}
		if (reason != null && !"Rejected".equalsIgnoreCase(decision)) {
			throw new InvalidDeferralStateException("A rejection reason can only be supplied when rejecting a deferral.");
		}

		Deferral deferral;
		try {
			deferral = deferralDAO.getDeferral(id);
		} catch (EmptyResultDataAccessException e) {
			throw new InvalidDeferralStateException("Deferral does not exist.");
		}
		if (deferral == null) {
			throw new InvalidDeferralStateException("Deferral does not exist.");
		}
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		boolean administrator = hasAuthority(authentication, "ROLE_ADMIN");
		if (authentication == null || !authentication.isAuthenticated()
				|| !lecturerId.equals(authentication.getName())) {
			throw new InvalidDeferralStateException("The authenticated account does not match the decision maker.");
		}
		if (!administrator && (!hasAuthority(authentication, "ROLE_LECTURER")
				|| !lecturerId.equals(deferral.getLectId()))) {
			throw new InvalidDeferralStateException("Lecturer is not authorised to decide this deferral.");
		}
		if (!"False".equalsIgnoreCase(deferral.getApproval())) {
			throw new InvalidDeferralStateException("Deferral is no longer pending.");
		}

		String normalisedDecision = "True".equalsIgnoreCase(decision) ? "True" : "Rejected";
		int updated = deferralDAO.updateDeferralStatusIfPending(id, normalisedDecision, lecturerId, reason);
		if (updated != 1) {
			throw new InvalidDeferralStateException("Deferral changed before the decision could be recorded.");
		}
	}

	private boolean hasAuthority(Authentication authentication, String authority) {
		if (authentication == null || authentication.getAuthorities() == null) {
			return false;
		}
		for (GrantedAuthority granted : authentication.getAuthorities()) {
			if (authority.equals(granted.getAuthority())) {
				return true;
			}
		}
		return false;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public int submitModuleDeferral(String studentId, String moduleId, int crnNumber,
			byte[] document, Path uploadDirectory) throws IOException {
		validateSubmissionDocument(document, uploadDirectory);
		rejectDuplicatePendingSubmission(studentId, moduleId, null);
		int id = deferralDAO.createModuleDeferralGetId(studentId, moduleId, crnNumber);
		ArrayList<Integer> ids = new ArrayList<Integer>();
		ids.add(id);
		writeDocuments(ids, document, uploadDirectory);
		return id;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public ArrayList<Integer> submitProgrammeDeferral(String studentId, String programmeId,
			byte[] document, Path uploadDirectory) throws IOException {
		validateSubmissionDocument(document, uploadDirectory);
		rejectDuplicatePendingSubmission(studentId, null, programmeId);
		ArrayList<Integer> ids = deferralDAO.createProgrammeDeferralGetId(studentId, programmeId);
		if (ids.isEmpty()) {
			throw new IllegalArgumentException("No modules are registered for this student and programme.");
		}
		writeDocuments(ids, document, uploadDirectory);
		return ids;
	}

	private void rejectDuplicatePendingSubmission(String studentId, String moduleId, String programmeId) {
		for (Deferral existing : deferralDAO.listDeferralsByStudent(studentId)) {
			if (!"False".equalsIgnoreCase(existing.getApproval())) {
				continue;
			}
			if (moduleId != null && moduleId.equals(existing.getModuleId())) {
				throw new IllegalArgumentException("A pending module deferral already exists.");
			}
			if (programmeId != null && programmeId.equals(existing.getProgrammeId())) {
				throw new IllegalArgumentException("A pending programme deferral already exists.");
			}
		}
	}

	private void validateSubmissionDocument(byte[] document, Path uploadDirectory) {
		if (document == null || document.length == 0 || uploadDirectory == null) {
			throw new IllegalArgumentException("A supporting document and upload directory are required.");
		}
	}

	private void writeDocuments(List<Integer> ids, byte[] document, Path uploadDirectory) throws IOException {
		final List<Path> createdFiles = new ArrayList<Path>();
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
			@Override
			public void afterCompletion(int status) {
				if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
					for (Path file : createdFiles) {
						try {
							Files.deleteIfExists(file);
						} catch (IOException e) {
							logger.error("Could not remove a document after deferral rollback: " + file, e);
						}
					}
				} else if (status != TransactionSynchronization.STATUS_COMMITTED) {
					logger.error("Deferral transaction outcome is unknown; inspect uploaded documents: " + createdFiles);
				}
			}
		});
		Files.createDirectories(uploadDirectory);
		for (Integer id : ids) {
			Path file = uploadDirectory.resolve(id + ".pdf");
			Files.createFile(file);
			createdFiles.add(file);
			Files.write(file, document, StandardOpenOption.WRITE);
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

