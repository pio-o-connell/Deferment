package com.defermentsystem.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.defermentsystem.domain.Semester;
import com.defermentsystem.repository.SemesterDAO;

@Service
public interface SemesterService {
	
	/**
	 * This is the method to be used to connect up with the SemesterDAO
	 */
	public void setSemesterDAO(SemesterDAO semesterDAO);
	
	/**
	 * This is the method to be used to list down all the records from the
	 * Semester table.
	 */
	public List<Semester> listSemesters();

}

