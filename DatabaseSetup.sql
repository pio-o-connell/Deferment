-- Deferment development database installation.
-- This script creates missing objects and inserts seed records without overwriting existing rows.
-- It does not drop an existing database or existing records.

CREATE DATABASE IF NOT EXISTS `spring_project`
  CHARACTER SET utf8;

USE `spring_project`;

CREATE TABLE IF NOT EXISTS `deferrals` (
  `Def_ID` int(11) NOT NULL,
  `Student_ID` varchar(10) NOT NULL,
  `Lect_ID` varchar(10) NOT NULL,
  `Programme_ID` varchar(20) NOT NULL,
  `Module_ID` varchar(10) NOT NULL,
  `Approved` varchar(10) NOT NULL DEFAULT 'False',
  `Decision_Lect_ID` varchar(10) DEFAULT NULL,
  `Rejection_Reason` varchar(1000) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `lecturer` (
  `Lect_ID` varchar(10) NOT NULL,
  `Firstname` varchar(20) NOT NULL,
  `Surname` varchar(30) NOT NULL,
  `Lecturer_Email` varchar(30) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `modules` (
  `Module_ID` varchar(10) NOT NULL,
  `CRN` int(10) NOT NULL,
  `Name` varchar(45) NOT NULL,
  `Lect_ID` varchar(20) NOT NULL,
  `Semester_ID` varchar(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `programme` (
  `Programme_ID` varchar(20) NOT NULL,
  `Num_Years` int(11) NOT NULL,
  `Coord_ID` varchar(10) NOT NULL,
  `Prog_Year` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `registration` (
  `Student_ID` varchar(10) NOT NULL,
  `CRN` int(10) NOT NULL,
  `Programme_ID` varchar(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `semester` (
  `Semester_ID` varchar(20) NOT NULL,
  `Programme_ID` varchar(20) DEFAULT NULL,
  `StartDate` enum('Sept','Jan') NOT NULL,
  `FinishDate` enum('Dec','May') NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `student` (
  `Student_ID` varchar(10) NOT NULL,
  `Firstname` varchar(20) NOT NULL,
  `Surname` varchar(30) NOT NULL,
  `Student_Email` varchar(30) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `users` (
  `USER_ID` INT(10) UNSIGNED NOT NULL,
  `USERNAME` VARCHAR(45) NOT NULL,
  `PASSWORD` VARCHAR(100) NOT NULL,
  `ENABLED` tinyint(1) NOT NULL,
  PRIMARY KEY (`USER_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `user_roles` (
  `USER_ROLE_ID` INT(10) UNSIGNED NOT NULL,
  `USER_ID` INT(10) UNSIGNED NOT NULL,
  `AUTHORITY` VARCHAR(45) NOT NULL,
  PRIMARY KEY (`USER_ROLE_ID`),
  KEY `FK_user_roles` (`USER_ID`),
  CONSTRAINT `FK_user_roles` FOREIGN KEY (`USER_ID`) REFERENCES `users` (`USER_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

ALTER TABLE `deferrals`
  ADD PRIMARY KEY (`Def_ID`,`Student_ID`),
  ADD KEY `Student_ID` (`Student_ID`),
  ADD KEY `Programme_ID` (`Programme_ID`),
  ADD KEY `Module_ID` (`Module_ID`),
  ADD KEY `Lect_ID` (`Lect_ID`);

ALTER TABLE `lecturer`
  ADD PRIMARY KEY (`Lect_ID`);

ALTER TABLE `modules`
  ADD PRIMARY KEY (`Module_ID`,`CRN`),
  ADD UNIQUE KEY `CRN` (`CRN`),
  ADD KEY `Lect_ID` (`Lect_ID`),
  ADD KEY `Semester_ID` (`Semester_ID`);

ALTER TABLE `programme`
  ADD PRIMARY KEY (`Programme_ID`),
  ADD KEY `Coord_ID` (`Coord_ID`);

ALTER TABLE `registration`
  ADD PRIMARY KEY (`Student_ID`,`CRN`),
  ADD KEY `CRN` (`CRN`),
  ADD KEY `Programme_ID` (`Programme_ID`);

ALTER TABLE `semester`
  ADD PRIMARY KEY (`Semester_ID`),
  ADD KEY `Programme_ID` (`Programme_ID`);

ALTER TABLE `student`
  ADD PRIMARY KEY (`Student_ID`);

ALTER TABLE `deferrals`
  MODIFY `Def_ID` int(11) NOT NULL AUTO_INCREMENT,
  AUTO_INCREMENT=782;

ALTER TABLE `deferrals`
  ADD CONSTRAINT `deferrals_ibfk_2`
    FOREIGN KEY (`Programme_ID`) REFERENCES `programme` (`Programme_ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  ADD CONSTRAINT `deferrals_ibfk_3`
    FOREIGN KEY (`Module_ID`) REFERENCES `modules` (`Module_ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  ADD CONSTRAINT `deferrals_ibfk_4`
    FOREIGN KEY (`Student_ID`) REFERENCES `student` (`Student_ID`) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE `modules`
  ADD CONSTRAINT `modules_ibfk_1`
    FOREIGN KEY (`Lect_ID`) REFERENCES `lecturer` (`Lect_ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  ADD CONSTRAINT `modules_ibfk_2`
    FOREIGN KEY (`Semester_ID`) REFERENCES `semester` (`Semester_ID`) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE `programme`
  ADD CONSTRAINT `programme_ibfk_1`
    FOREIGN KEY (`Coord_ID`) REFERENCES `lecturer` (`Lect_ID`) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE `registration`
  ADD CONSTRAINT `registration_ibfk_1`
    FOREIGN KEY (`Student_ID`) REFERENCES `student` (`Student_ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  ADD CONSTRAINT `registration_ibfk_2`
    FOREIGN KEY (`CRN`) REFERENCES `modules` (`CRN`) ON DELETE CASCADE ON UPDATE CASCADE,
  ADD CONSTRAINT `registration_ibfk_3`
    FOREIGN KEY (`Programme_ID`) REFERENCES `programme` (`Programme_ID`) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE `semester`
  ADD CONSTRAINT `semester_ibfk_2`
    FOREIGN KEY (`Programme_ID`) REFERENCES `programme` (`Programme_ID`) ON DELETE CASCADE ON UPDATE CASCADE;

INSERT IGNORE INTO `lecturer`
  (`Lect_ID`, `Firstname`, `Surname`, `Lecturer_Email`) VALUES
  ('L001', 'Mary', 'Griffin', 'MaryGriff@myCIT.ie'),
  ('L002', 'John', 'Murphy', 'JohnM@myCIT.ie'),
  ('L003', 'David', 'Johnson', 'DaveJohnson2@myCIT.ie');

INSERT IGNORE INTO `student`
  (`Student_ID`, `Firstname`, `Surname`, `Student_Email`) VALUES
  ('R0004444', 'Bill', 'O''Donoghue', 'billodonoghue17@myCIT.ie'),
  ('R0020934', 'Jean', 'Maguire', 'jeanmaguire6@myCIT.ie');

INSERT IGNORE INTO `programme`
  (`Programme_ID`, `Num_Years`, `Coord_ID`, `Prog_Year`) VALUES
  ('KCLDC_Y4', 4, 'L002', 4),
  ('KCMSD_Y5', 5, 'L001', 5);

INSERT IGNORE INTO `semester`
  (`Semester_ID`, `Programme_ID`, `StartDate`, `FinishDate`) VALUES
  ('KCLDC_Y4_S1', 'KCLDC_Y4', 'Sept', 'Dec'),
  ('KCLDC_Y4_S2', 'KCLDC_Y4', 'Jan', 'May'),
  ('KCMSD_Y5_S1', 'KCMSD_Y5', 'Jan', 'May'),
  ('KCMSD_Y5_S2', 'KCMSD_Y5', 'Sept', 'Dec');

INSERT IGNORE INTO `modules`
  (`Module_ID`, `CRN`, `Name`, `Lect_ID`, `Semester_ID`) VALUES
  ('COMP8016', 22780, 'Business Intelligence', 'L002', 'KCLDC_Y4_S1'),
  ('COMP8016', 24249, 'Business Intelligence', 'L002', 'KCMSD_Y5_S2'),
  ('COMP8038', 23317, 'Cloud Computing with Python', 'L001', 'KCLDC_Y4_S1'),
  ('COMP8040', 24248, 'Cloud Application Frameworks', 'L001', 'KCMSD_Y5_S2');

INSERT IGNORE INTO `registration`
  (`Student_ID`, `CRN`, `Programme_ID`) VALUES
  ('R0020934', 22780, 'KCLDC_Y4'),
  ('R0020934', 23317, 'KCLDC_Y4'),
  ('R0004444', 24248, 'KCMSD_Y5');

-- Development password for all six accounts: 123456
-- Hash generated and verified by the Spring Security 3.2.5 BCryptPasswordEncoder.
INSERT IGNORE INTO `users`
  (`USER_ID`, `USERNAME`, `PASSWORD`, `ENABLED`) VALUES
  (100, 'L001', '$2a$10$Gsb.74ftE6ftZQdm/p2rS.bKwGCvpF6ADvabvON1d.AAiu.cff.Eu', 1),
  (101, 'R0004444', '$2a$10$Gsb.74ftE6ftZQdm/p2rS.bKwGCvpF6ADvabvON1d.AAiu.cff.Eu', 1),
  (102, 'R0020934', '$2a$10$Gsb.74ftE6ftZQdm/p2rS.bKwGCvpF6ADvabvON1d.AAiu.cff.Eu', 1),
  (103, 'L002', '$2a$10$Gsb.74ftE6ftZQdm/p2rS.bKwGCvpF6ADvabvON1d.AAiu.cff.Eu', 1),
  (104, 'L003', '$2a$10$Gsb.74ftE6ftZQdm/p2rS.bKwGCvpF6ADvabvON1d.AAiu.cff.Eu', 1),
  (105, 'ADMIN', '$2a$10$Gsb.74ftE6ftZQdm/p2rS.bKwGCvpF6ADvabvON1d.AAiu.cff.Eu', 1);

INSERT IGNORE INTO `user_roles`
  (`USER_ROLE_ID`, `USER_ID`, `AUTHORITY`) VALUES
  (0, 100, 'ROLE_LECTURER'),
  (1, 101, 'ROLE_USER'),
  (2, 102, 'ROLE_USER'),
  (3, 103, 'ROLE_LECTURER'),
  (4, 104, 'ROLE_LECTURER'),
  (5, 105, 'ROLE_ADMIN');

INSERT IGNORE INTO `deferrals`
  (`Def_ID`, `Student_ID`, `Lect_ID`, `Programme_ID`, `Module_ID`,
   `Approved`, `Decision_Lect_ID`, `Rejection_Reason`) VALUES
  (1, 'R0004444', 'L001', 'KCMSD_Y5', 'COMP8040', 'False', NULL, NULL);
