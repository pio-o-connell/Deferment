<%@ include file="/WEB-INF/jsp/include.jsp"%>

<h2>Administrator dashboard</h2>
<p>Manage application records using the existing administrative workflows.</p>
<ul>
	<li><a href="<c:url value='/admin/student/listall'/>">Students</a></li>
	<li><a href="<c:url value='/admin/lecturer/listall'/>">Lecturers</a></li>
	<li><a href="<c:url value='/admin/module/listall'/>">Modules</a></li>
	<li><a href="<c:url value='/admin/programme/listall'/>">Programmes</a></li>
	<li><a href="<c:url value='/admin/deferral/listall'/>">Deferrals</a></li>
</ul>
