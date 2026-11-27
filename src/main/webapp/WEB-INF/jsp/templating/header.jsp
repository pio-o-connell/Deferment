<%@ include file="/WEB-INF/jsp/include.jsp"%> 
<html>
<a href="<%= request.getContextPath() %>/home" class="ui-btn ui-icon-home ui-btn-icon-left">Home</a>
<sec:authorize access="isAuthenticated()">
	<form:form action="${pageContext.request.contextPath}/j_spring_security_logout" method="post">
		<button type="submit" class="ui-btn ui-icon-search ui-btn-icon-right">Log out</button>
	</form:form>
</sec:authorize>
<sec:authorize access="isAnonymous()">
	<a href="<%= request.getContextPath() %>/login" class="ui-btn ui-icon-search ui-btn-icon-right">Log in</a>
</sec:authorize>
<a href="<%= request.getContextPath() %>/home" id="headerSpace">Home</a>
<head>
<title>Header</title>
<link href="<c:url value="/resources/css/Group2.css" />" rel="stylesheet"></link>
</head>
<body>
	<div id="header">

		<ul id="topNavList">
			<li class="first lev2"><a class="headerlink"
				href="http://www.cit.ie" title="CIT Website"><font
					color="#5C8AE6">Link to CIT website</font></a></li>
		</ul>
	</div>

	<div>

<img class= "books" src="https://studystrategies.yaia.com/studyindex.gif" style="width:204px;height:120px">
<a href="/SpringWebProject/home" class="title"><font color="white">CIT Deferral System</font></a>    

</div> 



</body>
</html>

