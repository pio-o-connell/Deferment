package com.defermentsystem.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.defermentsystem.domain.Semester;
import com.defermentsystem.repository.SemesterDAO;

@Service
public class SemesterServiceImpl implements SemesterService {
	
	@Autowired
	private SemesterDAO semesterDAO;

	@Override
	public void setSemesterDAO(SemesterDAO semesterDAO) {
		this.semesterDAO = semesterDAO;

	}

	@Override
	public List<Semester> listSemesters() {
		return semesterDAO.listSemesters();
	}

}

