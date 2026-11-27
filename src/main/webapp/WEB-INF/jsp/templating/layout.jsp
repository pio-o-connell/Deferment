<%@ include file="/WEB-INF/jsp/include.jsp"%> 
<html>  
<head>
<title>
<tiles:insertAttribute name="title" ignore="true"></tiles:insertAttribute></title>
<link rel="stylesheet" href="https://code.jquery.com/mobile/1.4.4/jquery.mobile-1.4.4.min.css">
<script src="https://code.jquery.com/jquery-1.11.1.min.js"></script>
<script src="https://code.jquery.com/mobile/1.4.4/jquery.mobile-1.4.4.min.js"></script>
<style>
th {
    border-bottom: 1px solid #d6d6d6;
}

tr:nth-child(even) {
    background: #e9e9e9;
}

td{
 border-top: 1px solid #d6d6d6;
 border-bottom: 1px solid #d6d6d6;
}
.error { color: #ff0000; }
.errorblock { 
color: #000; 
background-color: #ffEEEE;
border: 3px solid #ff0000; 
padding: 8px; 
margin: 16px; }
</style>

		
</head>

<body>

<div data-role="page">

<!--header--> 
<div data-role="header" data-position="fixed" data-theme="b" > 	
		<tiles:insertAttribute name="header"  />
</div> 

<!--main body with a panel to the left -->
		
<div data-role="main" class="ui-content" id="pageone">	
	<a href="#myPanelDefault" class="ui-btn ui-btn-inline ui-corner-all ui-shadow">Menu Options</a>
	<tiles:insertAttribute name="body" />
</div>			
		
<!--footer-->	
<div  data-role="footer" data-position="fixed" data-theme="b">
	<tiles:insertAttribute name="footer" />
</div>


<!-- Panel -->
<div data-role="panel" id="myPanelDefault" style="text-align:center;"> 
<div data-role="collapsibleset">
 
 <div id="panelImage">
<h1><a href="<%= request.getContextPath() %>/home">Home</a></h1>
</div>
	
	<div class="menuPosition">
		<sec:authorize access="isAnonymous()">
			<ul data-role="listview" data-inset="true">
				<li><a href="<c:url value='/home'/>">Home</a></li>
				<li><a href="<c:url value='/login'/>">Log in</a></li>
				<li><a href="<c:url value='/home/downloadForm'/>">Download deferral form</a></li>
			</ul>
		</sec:authorize>
		<sec:authorize access="hasRole('ROLE_USER')">
			<ul data-role="listview" data-inset="true">
				<li><a href="<c:url value='/student/dashboard'/>">Student dashboard</a></li>
				<li><a href="<c:url value='/admin/deferral/addModuleDeferralPage1'/>">Submit module deferral</a></li>
				<li><a href="<c:url value='/admin/deferral/addProgrammeDeferralPage1'/>">Submit programme deferral</a></li>
				<li><a href="<c:url value='/student/applications'/>">My applications</a></li>
			</ul>
		</sec:authorize>
		<sec:authorize access="hasRole('ROLE_LECTURER')">
			<ul data-role="listview" data-inset="true">
				<li><a href="<c:url value='/lecturer/dashboard'/>">Lecturer dashboard</a></li>
				<li><a href="<c:url value='/lecturer/pending'/>">Pending applications</a></li>
				<li><a href="<c:url value='/lecturer/processed'/>">Processed applications</a></li>
			</ul>
		</sec:authorize>
		<sec:authorize access="hasRole('ROLE_ADMIN')">
			<ul data-role="listview" data-inset="true">
				<li><a href="<c:url value='/admin/dashboard'/>">Administrator dashboard</a></li>
				<li><a href="<c:url value='/admin/student/listall'/>">Students</a></li>
				<li><a href="<c:url value='/admin/lecturer/listall'/>">Lecturers</a></li>
				<li><a href="<c:url value='/admin/module/listall'/>">Modules</a></li>
				<li><a href="<c:url value='/admin/programme/listall'/>">Programmes</a></li>
				<li><a href="<c:url value='/admin/deferral/listall'/>">Deferrals</a></li>
			</ul>
		</sec:authorize>
		
  </div>  
  

<a href="#pageone" data-rel="close" class="ui-btn ui-btn-inline ui-shadow ui-corner-all ui-btn-a ui-icon-delete ui-btn-icon-left">Close Menu</a>
</div> 
</div>	

</div>
</body>  
</html>