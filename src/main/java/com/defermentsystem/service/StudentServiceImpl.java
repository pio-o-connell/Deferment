package com.defermentsystem.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.defermentsystem.domain.Student;
import com.defermentsystem.repository.StudentDAO;

@Service
@Transactional(readOnly = true)
public class StudentServiceImpl implements StudentService {
	
	@Autowired
	private StudentDAO studentDAO;
	
	public void setStudentDAO(StudentDAO studentDAO){
		this.studentDAO = studentDAO;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void createStudent(String studId, String firstName, String lastName,
			String email) {
		studentDAO.createStudent(studId, firstName, lastName, email);

	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void updateStudent(String id, String firstname, String lastname,
			String email) {
		studentDAO.updateStudent(id, firstname, lastname, email);

	}
	
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void updateStudentEmail(String id, String email) {
		studentDAO.updateStudentEmail(id, email);
		
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void updateStudent(String id, String lastname) {
		studentDAO.updateStudent(id, lastname);

	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void deleteStudent(String studId) {
		studentDAO.deleteStudent(studId);

	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void createMultipleStudents(List<Student> students) {
		studentDAO.createMultipleStudents(students);

	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void deleteMultipleStudents(List<Student> students) {
		studentDAO.deleteMultipleStudents(students);

	}

	@Override
	public Student getStudent(String id) {
		
		return studentDAO.getStudent(id);
	}

	@Override
	public List<Student> listStudents() {
		
		return studentDAO.listStudents();
	}

	
	
	

}

