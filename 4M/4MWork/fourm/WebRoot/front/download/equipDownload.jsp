<%@ page contentType="text/html; charset=GBK" import="java.io.*" import="com.jspsmart.upload.*"%>
<%@ include file="/front/common/taglib.jsp"%>

<%
String name= request.getParameter("name");
String path = request.getContextPath();
System.out.println(path);
if("".equals(name) || name==null){
	return;
}
super.init(config);
String appPath = config.getServletContext().getRealPath(""); // tomcat������·��
System.out.println("appPath:"+appPath);
System.out.println(appPath.substring(0,appPath.length()-13));
String realPath = appPath.substring(0,appPath.length()-13);
// �½�һ��SmartUpload����
SmartUpload su = new SmartUpload();
// ��ʼ��
su.initialize(pageContext);
// �趨contentDispositionΪnull�Խ�ֹ������Զ����ļ���
//���������acrobat�򿪡�
su.setContentDisposition(null);
realPath=realPath.replace('\\','/');
// �����ļ�   ˵����
String downloadPath = realPath+"webapps/manage/upload/"+name;
System.out.println("downloadPath:"+downloadPath);
su.downloadFile(downloadPath);
out.clear();
out = pageContext.pushBody();
%>