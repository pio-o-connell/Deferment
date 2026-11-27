<%@ include file="/WEB-INF/jsp/include.jsp"%>

<h2>Student dashboard</h2>
<p>Submit a deferral and review only applications associated with your account.</p>
<ul>
	<li><a href="<c:url value='/admin/deferral/addModuleDeferralPage1'/>">Submit a module deferral</a></li>
	<li><a href="<c:url value='/admin/deferral/addProgrammeDeferralPage1'/>">Submit a programme deferral</a></li>
	<li><a href="<c:url value='/student/applications'/>">My applications</a></li>
</ul>
