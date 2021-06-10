<%--
  Created by IntelliJ IDEA.
  User: teo07
  Date: 07.06.2021
  Time: 21:07
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<html>
<head>
    <title>Meals</title>
    <style>
        table {
            border: 1px solid black;
            border-collapse: collapse;
        }

        td {
            border: 1px solid black;
            padding-top: 5px;
            padding-bottom: 5px;
            padding-left: 15px;
            padding-right: 15px;
        }

        td#td01 {
            text-align: right;
        }
    </style>
</head>
<body>
<h3><a href="index.html">Home</a></h3>
<h2>Meals</h2>
<c:if test="${empty mealWithExceedList}">
    There no meals
</c:if>
<c:if test="${not empty mealWithExceedList}">
    <table>
        <c:forEach var="meal" items="${mealWithExceedList}">
            <c:if test="${meal.exceed == true}">
                <tr style="color: red">
            </c:if>
            <c:if test="${meal.exceed == false}">
                <tr style="color: green">
            </c:if>
            <c:set var="dateTime" value="${fn:replace(meal.dateTime, 'T', ' ')}"/>
            <td>${dateTime}</td>
            <td>${meal.description}</td>
            <td id="td01">${meal.calories}</td>
            </tr>
        </c:forEach>
    </table>
</c:if>
</body>
</html>