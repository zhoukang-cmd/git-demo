<%@ page contentType="text/html; charset=GBK" import="java.io.*" import="com.jspsmart.upload.*"%><%
// �½�һ��SmartUpload����
SmartUpload su = new SmartUpload();
// ��ʼ��
su.initialize(pageContext);
// �趨contentDispositionΪnull�Խ�ֹ������Զ����ļ���
//���������acrobat�򿪡�
su.setContentDisposition(null);
// �����ļ�
String downloadPath = "/downloads/videopackage-build20120228.exe";
su.downloadFile(downloadPath);
 out.clear();
 out = pageContext.pushBody();
%>