package com.defermentsystem.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.defermentsystem.domain.Programme;
import com.defermentsystem.repository.ProgrammeDAO;

@Service
@Transactional(readOnly = true)
public class ProgrammeServiceImpl implements ProgrammeService {

	@Autowired
	private ProgrammeDAO programmeDAO;
	
	public void setProgrammeDAO(ProgrammeDAO programmeDAO){
		this.programmeDAO = programmeDAO;
	}
	
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void createProgramme(String programmeId, int numYears,
			String coordinatorId, String startMonth) {
		programmeDAO.createProgramme(programmeId, numYears, coordinatorId, startMonth);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public List<Programme> deleteProgramme(String programmeId) {
		return programmeDAO.deleteProgramme(programmeId);
	}

	@Override
	public String getStudentProgrammeId(String studId) {
		return programmeDAO.getStudentProgrammeId(studId);
	}

	@Override
	public List<Programme> listProgrammes() {
		return programmeDAO.listProgrammes();
	}

	@Override
	public Programme getProgramme(String progId) {
		return programmeDAO.getProgramme(progId);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Programme updateProgrammeCoordinator(String progId,
			String coordinatorId) {
		return programmeDAO.updateProgrammeCoordinator(progId, coordinatorId);
	}

}

