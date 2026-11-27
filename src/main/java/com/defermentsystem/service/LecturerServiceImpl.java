package com.defermentsystem.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.defermentsystem.domain.Lecturer;
import com.defermentsystem.repository.LecturerDAO;

@Service
@Transactional(readOnly = true)
public class LecturerServiceImpl implements LecturerService {

	@Autowired
	private LecturerDAO lecturerDAO;
	
	@Override
	public void setLecturerDAO(LecturerDAO lecturerDAO) {
		this.lecturerDAO = lecturerDAO;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void createLecturer(String lectId, String firstName,
			String lastName, String email) {
		lecturerDAO.createLecturer(lectId, firstName, lastName, email);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void updateLecturer(String id, String firstname, String lastname,
			String email) {
		lecturerDAO.updateLecturer(id, firstname, lastname, email);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void updateLecturer(String id, String lastname) {
		lecturerDAO.updateLecturer(id, lastname);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void deleteLecturer(String lectId) {
		lecturerDAO.deleteLecturer(lectId);

	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void createMultipleLecturers(List<Lecturer> lecturers) {
		lecturerDAO.createMultipleLecturers(lecturers);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void deleteMultipleLecturers(List<Lecturer> lecturers) {
		lecturerDAO.deleteMultipleLecturers(lecturers);

	}

	@Override
	public Lecturer getLecturer(String id) {
		return lecturerDAO.getLecturer(id);
	}

	@Override
	public List<Lecturer> listLecturers() {
		return lecturerDAO.listLecturers();
	}

}

