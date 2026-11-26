package com.defermentsystem.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.defermentsystem.domain.Deferral;
import com.defermentsystem.repository.DeferralDAO;

public interface DeferralService {
	String DECISION_LECTURER_ID = "L001";
	
	/**
	 * This is the method to be used to connect up with the DeferralDAO
	 */
	public void setDeferralDAO(DeferralDAO deferralDAO);

	/**
	 * This is the method to be used to create a Deferral entry and return the
	 * id generated.
	 */
	public int createDeferralGetId(String studentID, String lectID,
			String progID, String moduleID);

	/**
	 * This is the method to be used to create a Module Deferral.
	 */
	public void createModuleDeferral(String studentId, String moduleId,
			int crnNumber);

	/**
	 * This is the method to be used to create a Programme Deferral .
	 */
	public void createProgrammeDeferral(String studentId, String programmeId);

	/**
	 * This is the method to be used to delete a Deferral entry.
	 */
	public void deleteDeferralById(int id);

	/**
	 * This is the method to be used to delete a Deferral based on a student ID.
	 */
	void deleteDeferralsByStudentID(String studentId);

	/**
	 * This is the method to be used to delete a Deferral based on a module ID.
	 */
	void deleteDeferralsByModuleID(String moduleId);

	/**
	 * This is the method to be retrieve a Deferral based on the ID.
	 */
	public Deferral getDeferral(int id);

	/**
	 * This is the method to be used to list down all the records from the
	 * deferral table.
	 */
	public List<Deferral> listDeferrals();

	/**
	 * This is the method to be used to list all deferrals of a particular
	 * student.
	 */
	public List<Deferral> listDeferralsByStudent(String studentID);

	/**
	 * This is the method to be used to list all deferrals of a particular
	 * module.
	 */
	public List<Deferral> listDeferralsByModule(String moduleID);
	
	/**
	 * This is the method to be used to list all deferrals of a particular
	 * programme.
	 */
	public List<Deferral> listDeferralsByProgramme(String programmeID);

	/**
	 * This is the method to be used to list all unapproved deferrals.
	 */
	public List<Deferral> listUnapprovedDeferrals(String unapproved);

	/**
	 * This is the method to be used to list all unapproved deferrals of a
	 * particular programme.
	 */
	public List<Deferral> listUnapprovedDeferralsByProgramme(String unapproved,
			String programmeID);

	/**
	 * This is the method to be used to list all unapproved deferrals by
	 * coodinator.
	 */
	public List<Deferral> listUnapprovedDeferralsByProgrammeCoordinatorName(
			String unapproved, String firstname);

	/**
	 * This is the method to be used to list all unapproved deferrals of a
	 * particular programme.
	 */
	public List<Deferral> listApprovedDeferralsByStudent(String approved,
			String studentID);

	/**
	 * Approve or reject a pending deferral as lecturer L001.
	 */
	public void decideDeferral(int id, String lecturerId, String decision);

	public void decideDeferral(int id, String lecturerId, String decision, String rejectionReason);

	int submitModuleDeferral(String studentId, String moduleId, int crnNumber, byte[] document, Path uploadDirectory) throws IOException;

	ArrayList<Integer> submitProgrammeDeferral(String studentId, String programmeId, byte[] document, Path uploadDirectory) throws IOException;
	
	/**
	 * This is the method to be used to create a Module Deferral and return the id generated
	 */
	int createModuleDeferralGetId(String studentId, String moduleId,
			int crnNumber);
	/**
	 * This is the method to be used to create a Programme Deferral and return an ArrayList of id's generated
	 */
	ArrayList<Integer> createProgrammeDeferralGetId(String studentId,
			String programmeId);
	
	/**
	 *This is the method to list deferrals by coordinator
	 */
	public List<Deferral> listDeferralsByCoordinatorId(String coordinatorId);
	
	/**
	 *This is the method to list unapproved deferrals by coordinator
	 */
	public List<Deferral> listUnapprovedDeferralsByCoordinatorId(String coordinatorId);

}

