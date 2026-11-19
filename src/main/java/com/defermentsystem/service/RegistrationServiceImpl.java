package com.defermentsystem.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.defermentsystem.domain.Registration;
import com.defermentsystem.repository.RegistrationDAO;

@Service
public class RegistrationServiceImpl implements RegistrationService {
	
	@Autowired
	private RegistrationDAO registrationDAO;

	@Override
	public void setRegistrationDAO(RegistrationDAO registrationDAO) {
		this.registrationDAO = registrationDAO;
	}

	@Override
	public List<Registration> listRegistrations() {
		// TODO Auto-generated method stub
		return registrationDAO.listRegistrations();
	}

}

