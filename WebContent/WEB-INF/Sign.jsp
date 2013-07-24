<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title>PKIProject</title>
</head>
<body>
    <%@ include file="Header.jsp"%>
    
    <link href="css/menu_assets/styles.css" rel="stylesheet" type="text/css">

<div id='cssmenu'>
<ul>
   <li class='first'><a href='Crypto'><span>Génération de certificats</span></a></li>
    <li><a href='CRLgen'><span>Génération de la CRL</span></a></li>
    <li class='active'><a href='Sign'><span>Signer un fichier</span></a></li>
   <li class='last'><a href='#'><span>Contact</span></a></li>
</ul>
</div>
    
<h2 align="center"><br><br>Signer un fichier<br><br></h2>
<form action="Sign" method="POST" enctype="multipart/form-data">
		
		<br>
		<br>
		 <br>
		<input type="file" value="Signer un fichier" >
	<input type="submit" value="Signer un fichier">
	</form>

</body>
</html>