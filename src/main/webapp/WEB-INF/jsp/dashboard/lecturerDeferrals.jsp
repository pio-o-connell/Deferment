<%@ include file="/WEB-INF/jsp/include.jsp"%>

<h2>Applications assigned to your lecturer account</h2>
<c:choose>
	<c:when test="${not empty deferrals}">
		<table data-role="table" class="ui-responsive">
			<thead>
				<tr><th>Application</th><th>Student</th><th>Programme</th><th>Module</th><th>Status</th><th>Document</th><th>Decision</th></tr>
			</thead>
			<tbody>
				<c:forEach var="deferral" items="${deferrals}">
					<tr>
						<td><c:out value="${deferral.defId}"/></td>
						<td><c:out value="${deferral.studentId}"/></td>
						<td><c:out value="${deferral.programmeId}"/></td>
						<td><c:out value="${deferral.moduleId}"/></td>
						<td><c:out value="${deferral.approval}"/></td>
						<td><a href="<c:url value='/admin/deferral/downloadForm/id/${deferral.defId}'/>">Download supporting document</a></td>
						<td>
							<c:if test="${deferral.approval=='False'}">
								<a href="<c:url value='/admin/deferral/modify/id/${deferral.defId}'/>">Review</a>
							</c:if>
						</td>
					</tr>
				</c:forEach>
			</tbody>
		</table>
	</c:when>
	<c:otherwise>
		<p>No applications are available for this view.</p>
	</c:otherwise>
</c:choose>
