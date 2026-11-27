<%@ include file="/WEB-INF/jsp/include.jsp"%>

<h2>Lecturer dashboard</h2>
<p>Review applications assigned to your lecturer account.</p>
<ul>
	<li><a href="<c:url value='/lecturer/pending'/>">Pending applications</a></li>
	<li><a href="<c:url value='/lecturer/processed'/>">Processed applications</a></li>
</ul>
