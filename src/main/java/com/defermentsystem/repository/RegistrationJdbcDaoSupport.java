package com.defermentsystem.repository;

import java.util.List;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.support.JdbcDaoSupport;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.defermentsystem.domain.Registration;
import com.defermentsystem.domain.mappers.RegistrationMapper;
import com.defermentsystem.repository.RegistrationDAO;

@Repository
public class RegistrationJdbcDaoSupport extends JdbcDaoSupport implements
		RegistrationDAO {

	@Autowired
	RegistrationJdbcDaoSupport(DataSource dataSource) {
		setDataSource(dataSource);
	}

	@SuppressWarnings("unchecked")
	@Override
	@Transactional(readOnly = false, propagation = Propagation.REQUIRED)
	public List<Registration> listRegistrations() {
		String SQL = "select * from registration";
		List<Registration> registrationList = getJdbcTemplate().query(SQL,new RegistrationMapper());
		return registrationList;
	}

	@Override
	@Transactional(readOnly = false, propagation = Propagation.REQUIRED)
	public int countRows() {
		String SQL = "select count(*) from registration";
		int rows = getJdbcTemplate().queryForObject(SQL, Integer.class);
		return rows;
	}

}

