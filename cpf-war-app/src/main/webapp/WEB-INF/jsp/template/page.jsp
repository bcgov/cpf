<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false"%><%@
  taglib
  uri="http://java.sun.com/jsp/jstl/core" prefix="c"%><%@
  taglib
  uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%><!DOCTYPE html>
<html xml:lang="en">
<head>
<meta charset="utf-8"/>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
<meta http-equiv="X-UA-Compatible" content="IE=Edge" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title><c:out value="${title}" /></title>
<c:forEach var="cssUrl" items="${cssUrls}"><c:if test="${not empty cssUrl}">
  <link href="<c:url value="${cssUrl}" />" rel="stylesheet" type="text/css" /></c:if></c:forEach>
<c:forEach var="javascriptUrl" items="${javascriptUrls}">
  <script type="text/javascript" src="<c:url value="${javascriptUrl}" />">
  </script></c:forEach>
<c:forEach var="alternateLink" items="${alternateLinks}">
  <link rel="alternate" type="<c:out value="${alternateLink.type}" />"
    title="<c:out value="${alternateLink.title}" />" href="<c:out value="${alternateLink.href}" />" />
</c:forEach>
</head>
<body>
  <c:choose>
    <c:when test="${param.plain == 'true'}">
      <div class="plain">
        <c:if test="${!empty(body)}">
          <c:import url="${body}" charEncoding="UTF-8" />
        </c:if>
      </div>
    </c:when>
    <c:otherwise>
  <header class="header">
<c:import url="/view/header/mainMenu" charEncoding="UTF-8" />
  </header>
  
  <c:if test="${!empty(breadcrumbUrl)}">
    <c:import url="${breadcrumbUrl}" charEncoding="UTF-8" />
  </c:if>
  <div class="container">
    <c:if test="${!empty(pageHeading)}">
      <h1><c:out value="${pageHeading}" /></h1>
    </c:if>
    <c:if test="${!empty(body)}">
      <c:import url="${body}" charEncoding="UTF-8" />
    </c:if>
  </div>
  <footer class="footer">
    <c:import url="/view/footer/footerMenu" charEncoding="UTF-8" />
    <!-- ${applicationCommit} -->
  <div class="version"><small>
    (v <c:out value="${applicationVersion}" /> )
    <c:set var="now" value="<%=new java.util.Date()%>" />
    [<fmt:formatDate value="${now}" pattern="yyyy-MM-dd HH:mm:ss" />]
  </small></div>
  </footer>
    </c:otherwise>
  </c:choose>
</body>
</html>