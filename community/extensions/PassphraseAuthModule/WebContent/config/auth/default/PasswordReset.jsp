<%--
/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright © 2012 ForgeRock Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [2012] [Forgerock AS]"
 */
--%>
<%@ page contentType="text/html" %>

<%@taglib uri="/WEB-INF/jato.tld" prefix="jato"%>
<%@taglib uri="/WEB-INF/auth.tld" prefix="auth"%>
<%@include file="init.jsp"%>

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<jato:useViewBean className="com.sun.identity.authentication.UI.LoginViewBean">
	<html xmlns="http://www.w3.org/1999/xhtml">
		<head>
			<title>Password Reset</title>
			<%
				String ServiceURI = (String) viewBean.getDisplayFieldValue(viewBean.SERVICE_URI);
				String encoded = "false";
				String gotoURL = (String) viewBean.getValidatedInputURL(request.getParameter("goto"), request.getParameter("encoded"), request);
				String encodedQueryParams = (String) viewBean.getEncodedQueryParams(request);
				if ((gotoURL != null) && (gotoURL.length() != 0)) {
				    encoded = "true";
				}
			%>
			<link rel="stylesheet" href="<%= serviceURL %>/css/main.css" type="text/css" />
			<!--[if lt IE 8]>
			<link rel="stylesheet" type="text/css" href="<%=serviceURL%>/css/ielt8.css" />
			<![endif]-->
			<!--[if IE 7]>
			<link rel="stylesheet" type="text/css" href="<%=serviceURL%>/css/ie7.css" />
			<![endif]-->
			<!--[if IE 6]>
			<link rel="stylesheet" type="text/css" href="<%=serviceURL%>/css/ie6.css" />
			<![endif]-->
			<script language="JavaScript" src="<%= ServiceURI %>/js/browserVersion.js"></script>
			<script language="JavaScript" src="<%= ServiceURI %>/js/auth.js"></script>
			
			<script language="JavaScript">
			    writeCSS('<%= ServiceURI %>');
			
				<jato:content name="validContent">
				    var defaultBtn = 'Submit';
				    var elmCount = 0;
				
				    function defaultSubmit() {
				        LoginSubmit(defaultBtn);
				    }
				
				    function LoginSubmit(value) {
				        aggSubmit();
				        var hiddenFrm = document.forms['Login'];
				        if (hiddenFrm != null) {
					    hiddenFrm.elements['IDButton'].value = value;
				            if (this.submitted) {
				                alert("The request is currently being processed");
				            } else {
				                this.submitted = true;
				                hiddenFrm.submit();
				            }
				        }
				    }
				</jato:content>
			</script>
			<script type="text/javascript"> <!--// Empty script so IE5.0 Windows will draw table and button borders //--> </script>
		</head>
		<body onload="placeCursorOnFirstElm();">			
				<div id="outer">
					<div id="container">
						<jato:content name="ContentStaticTextHeader">
								<div class="indented">
									<div style="color:red;text-align:center">
										<jato:getDisplayFieldValue name='StaticTextHeader' defaultValue='Authentication' fireDisplayEvents='true' escape='false' />
										<br/><br/>
									</div>
								</div>	
						</jato:content>	
						<div id="innerPR">
							<p class="indented">welcome to .<br/>
							Please enter your New Password and Confirm Password below.</p>
							<div id="logo"><a href="/"><img src="<%=serviceURL%>/images/logo150.gif" alt=". Logo" /></a></div>
							<table border="0" cellspacing="0" cellpadding="0">
								<jato:content name="validContent">
									<jato:tiledView name="tiledCallbacks" type="com.sun.identity.authentication.UI.CallBackTiledView">
										<script language="javascript">
											elmCount++;
										</script>
										<jato:content name="password">
											<form name="frm<jato:text name="txtIndex" />" action="blank" onSubmit="defaultSubmit(); return false;" method="post">
												<input class="indentedPR<jato:text name="txtIndex" />" type="password" name="IDToken<jato:text name="txtIndex" />" id="IDToken<jato:text name="txtIndex" />" value="" class="textbox">
											</form>
										</jato:content>
										<jato:content name="choice">
											<form name="frm<jato:text name="txtIndex" />" action="blank" onSubmit="defaultSubmit(); return false;" method="post">
												<jato:tiledView name="tiledChoices" type="com.sun.identity.authentication.UI.CallBackChoiceTiledView">
													<jato:content name="selectedChoice">
														<input type="hidden" name="IDToken<jato:text name="txtParentIndex" />" id="IDToken<jato:text name="txtParentIndex" />" value="1">
													</jato:content>
												</jato:tiledView>
											</form>
										</jato:content>
									</jato:tiledView>
								</jato:content>
                            </table>            				
							<jato:content name="ContentStaticTextResult">
								<p><b><jato:getDisplayFieldValue name='StaticTextResult' defaultValue='' fireDisplayEvents='true' escape='false'/></b></p>
							</jato:content>
					
							<jato:content name="ContentHref">
								<p><auth:href name="LoginURL" fireDisplayEvents='true'> <jato:text name="txtGotoLoginAfterFail" /></auth:href></p>
							</jato:content>
					
							<jato:content name="ContentImage">
								<p><img name="IDImage" src="<jato:getDisplayFieldValue name='Image'/>" alt=""></p>
							</jato:content>
			
							<jato:content name="ContentButtonLogin">								
									<jato:content name="hasButton">
										<script language="javascript">
											defaultBtn = '<jato:text name="defaultBtn" />';
										</script>
										<jato:tiledView name="tiledButtons" type="com.sun.identity.authentication.UI.ButtonTiledView">            
											<input type="submit" class="button" value="Submit" onClick="javascript:LoginSubmit('<jato:text name="txtButton" />')"/>
										</jato:tiledView>        
									</jato:content>
									<jato:content name="hasNoButton">
										<input class="indentedPR3" type="submit" class="button" value="Submit" onClick="javascript:LoginSubmit('<jato:text name="lblSubmit" />')"/>
									</jato:content>								
							</jato:content>
							<jato:content name="validContent">
								<auth:form name="Login" method="post" defaultCommandChild="DefaultLoginURL" >
									<script language="javascript">
										if (elmCount != null) {
											for (var i = 0; i < elmCount; i++) {
												document.write("<input name=\"IDToken" + i + "\" type=\"hidden\">");
											}
											document.write("<input name=\"IDButton"  + "\" type=\"hidden\">");
										}
									</script>
									<input type="hidden" name="goto" value="<%= gotoURL %>">
									<input type="hidden" name="SunQueryParamsString" value="<%= encodedQueryParams %>">
									<input type="hidden" name="encoded" value="<%= encoded %>">
									<input type="hidden" name="state" value="2">
								</auth:form>
							</jato:content>
						</div>						
						<br/>
						<div class="indentedPR4">
							Note:<br/>
							Please select a new password ensuring it contains 8 characters with at least three from the following categories<br/>
							- English upper case character<br/>
							- English lower case character<br/>
							- Numeric value (0-9)<br/>
							- Special character (e.g. ? #, %)<br/>
							Your new password will be used to verify your access to the Portal in future
						</div>
					<div id="footerPR"></div>	
				</div>								
			</div>			
		</body>
	</html>
</jato:useViewBean>