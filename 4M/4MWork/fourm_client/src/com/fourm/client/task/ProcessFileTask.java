package com.fourm.client.task;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fourm.common.TcpClient;
import com.fourm.common.FourmFtpClient;
import com.fourm.common.Utils;
import com.fourm.common.ZipUtil;

/**
 * �ͻ����ļ�����
 * 1.���sourceĿ¼Դ�ļ���zip�ļ�����zipĿ¼���ѱ�����ļ�����histĿ¼
 * 2.����zip�ļ����ɹ�����successĿ¼��ʧ������failĿ¼
 * @author zhangtaichao , Mobile Bank System, CSII
 * <p>created on 2012-2-29 </p>
 * modified by wangzhe 20121007 ����FTPClient�������õ�����������߳�����
 * modified by wangzhe 20121009 ����FTPClient�׳��쳣û��catch���ر�
 */
public class ProcessFileTask {
	
	private static Logger logger = LoggerFactory.getLogger(ProcessFileTask.class);
	private String sourcePath;
	private String histPath;
	private String zipPath;
	private String successPath;
	private String failPath;
	private String serverPath;
	private FourmFtpClient ftpClient;
	private TcpClient tcpClient;
	private String suffix = "";
	private int maxLine = 3600;//���������ļ������������
		
	private SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMddHHmmss");
	
	@SuppressWarnings("unchecked")
	public void process() {
		try {
			logger.debug("processFileTask begin...");
			pathCheck();
			
			File sourceDir = new File(getSourcePath());
			Collection<File> c = FileUtils.listFiles(sourceDir,getSuffix().split(","), false);

			for(Iterator<File> i = c.iterator();i.hasNext();) {
				File f = i.next();
				String name = f.getName();

				Calendar cal = Calendar.getInstance();
				String thisHour = new SimpleDateFormat("yyyyMMddHH").format(cal.getTime());//��ǰСʱ
				
				if(name.endsWith(".lvm") && name.startsWith("H") && (name.indexOf(thisHour) != -1)) { //��ǰСʱ��������
					logger.info("start to process file:" + f.getAbsolutePath());
					processSingleFile(f.getAbsolutePath());
					logger.info("end process:" + f.getAbsolutePath());
				} else if(name.endsWith(".dat") && name.startsWith("L") && (name.indexOf(thisHour) != -1)) { //��ǰСʱ�ķ�������
					logger.info("start to process file:" + f.getAbsolutePath());
					processDatFile(f.getAbsolutePath(),false);
					logger.info("end process:" + f.getAbsolutePath());
				} else {
					//TODO Ҫ�ѷǵ�ǰСʱ������(������δ�����)������ʷĿ¼����ȡ��ע������Ĵ��롣
					//move(f.getAbsolutePath(), getHistPath()+"/"+name);
				}
			}
			logger.debug("processFileTask end...");
		} catch (Throwable e) {
			logger.error("Final.. Exception:"+ Utils.printStackTrace(e));
		}
	}
	
	/**
	 * ������������ļ�
	 * @param filePath
	 * @param �Ƿ����ļ������һ��
	 * @return
	 * @throws IOException 
	 * @throws FileNotFoundException 
	 */
	private boolean processDatFile(String filePath,boolean end)  {
		BufferedReader br = null;
		BufferedWriter bw = null;
		FileInputStream fin = null;
		FileOutputStream fout = null;
		try {
			//��ȡline.properties�ļ�������������򴴽����ļ�
			String lineFileName = "line.properties";
			lineFileName = getSourcePath() + "/" + lineFileName;
			File lineFile = new File(lineFileName);
			if(!lineFile.exists()) {
				lineFile.createNewFile();
			}
			
			//��ȡ���ļ��ϴζ�ȡ���к�line.properties���������������кţ��򲻴���ֱ�ӽ����ƶ���hist�ļ�����
			int line = 0;
			Properties prop = new Properties();			
			fin = new FileInputStream(lineFile);
			prop.load(fin);
			File datFile = new File(filePath);
			File tmpFile = new File(filePath + ".4tmp");
			String strLine = prop.getProperty(filePath);
			if(StringUtils.isNotEmpty(strLine)) {
				line = Integer.parseInt(strLine);
			}
			String hist = getHistPath() + "/" + datFile.getName();
			if(line == getMaxLine()) {
				move(filePath,hist);
				return true;//�ļ��Ѿ��������
			}
			
			//Ϊ�˷�ֹ�ڶ��Ĺ����У���labview������д�����ݣ����ｫ�俽��һ�ݣ�Ȼ���ٽ��ж���
			FileUtils.copyFile(datFile, tmpFile);//����ǰ�ļ�����һ������ʱ�ļ�
			String partName = (line+1) + "_part_" + datFile.getName();
			File toZipFile = new File(getSourcePath() + "/" + partName);
			
			//����ʱ�ļ��е����ݶ�ȡ���ڴ��У��������������У�����ļ���������ֱ�ӽ����ƶ���hist�ļ��С�
			br = new BufferedReader(new InputStreamReader(new FileInputStream(tmpFile)));
			int tmp = 0;
			while(tmp < line) {
				if(br.ready()) {
					br.readLine();
					tmp++;
				} else {
					line = getMaxLine();
					move(filePath,hist);
					return true;//�ļ��Ѿ��������
				}
			}
			
			//����line.properties�ı�����к��Ժ󣬿�ʼ��ȡ�µ����ݣ������䱣������ʱ�ļ���
			int currentLine = 0;//�˴��¶�ȡ������
			do {
				String str = br.readLine();
				if(str == null && !end) {//�ļ�û�������ݽ������ֲ������һ�δ���
					return true;
				}
				
				if(br.ready()) {//���к������ݣ������һ��
					if(bw == null) {
						bw = new BufferedWriter(new FileWriter(toZipFile));
					}
					bw.write(str);
					bw.newLine();
					line++;
					currentLine++;
				} else {//����Ϊ���һ�У��������һ�δ������п���ֻΪһ�룬��������
					if(end) {
						if(str != null) {
							if(bw == null) {
								bw = new BufferedWriter(new FileWriter(toZipFile));
							}
							bw.write(str);
							bw.newLine();
							currentLine++;
						}
						line = getMaxLine();
					}
				}
				
			} while(br.ready());
			if(bw != null) {
				bw.flush();
				bw.close();
			}
			br.close();
			
			//������ζ�ȡ������Ϊ0���򷵻�
			if(currentLine == 0) {
				FileUtils.forceDelete(tmpFile);
				return true;
			}
			
			//����ʱ�ļ�ѹ����zip�ļ�
			String zipName = partName + ".zip";			
			boolean result = ZipUtil.zip(getSourcePath() + "/" + partName, getZipPath() + "/" + zipName);
			if(result) {//ѹ���ɹ�
				logger.debug("file zip success:" + getSourcePath() + "/" + partName);
				FileUtils.forceDelete(toZipFile);
				FileUtils.forceDelete(tmpFile);
				transfer(zipName, getZipPath() + "/" + zipName, false);
			}
			prop.put(filePath, line+"");
			fout = new FileOutputStream(lineFile);
			prop.store(fout, "can not be deleted");
			fin.close();
			fout.close();
		} catch(Exception e) {
			logger.error(Utils.printStackTrace(e));
		} finally {
			if(br != null) {
				try {
					br.close();
				} catch (IOException e) {
				}
			}
			if(bw != null) {
				try {
					bw.close();
				} catch (IOException e) {
				}
			}
			if(fin != null) {
				try {
					fin.close();
				} catch (IOException e) {
				}
			}
			if(fout != null) {
				try {
					fout.close();
				} catch (IOException e) {
				}
			}
		}
		
		return true;
	}

	/**
	 * ����.lvm�������ļ�
	 * @param filePath
	 * @return
	 */
	private boolean processSingleFile(String filePath) {
		File file = new File(filePath);
		String fileName = file.getName();
		String zipName = fileName + ".zip";
		boolean result = ZipUtil.zip(file.getAbsolutePath(), getZipPath()+"/"+zipName);
		if(result) {//ѹ���ɹ�,Դ�ļ�ת��histĿ¼
			logger.info("file zip success:" + fileName);
			move(file.getAbsolutePath(), getHistPath()+"/"+fileName);
			transfer(zipName, getZipPath() + "/" + zipName, false);
		}
		return result;//zip�ɹ�
	}
	
	private void transfer(String zipName,String zipPath,boolean fail) {
		try {
			transferAndMove(zipName, zipPath, false);
		} catch(Exception e) {			
			logger.error("ftp send error:\n" + Utils.printStackTrace(e));
			String failPath = getFailPath() + "/" + zipName;
			move(zipPath,failPath);		
		}
	}
		
	/**
	 * ��zipPath������ļ��ϴ��������Ŀ¼���������˽����ϴ����
	 * �ϴ��ɹ�����zipPathת����successĿ¼��ʧ����ת����failĿ¼
	 * @param zipName �ļ�����
	 * @param zipPath �ļ�ȫ·��
	 * @param failflag �Ƿ�ΪfailĿ¼�ڵ��ļ�
	 * @return
	 * @throws Exception 
	 */
	private boolean transferAndMove(String zipName,String zipPath,boolean failflag) throws Exception {
		logger.debug("transferAndMove()--> start to transfer ready: " + zipPath);
		String sendDate = getDateStr();
		//��FTP�����ϴ�
		try {
			ExecutorService executor = Executors.newSingleThreadExecutor();
			FutureTask<Boolean> task = new FutureTask<Boolean>(new FutureTransferTask(getFtpClient(), zipPath, getServerPath()));
			executor.execute(task);
			
			try {
				@SuppressWarnings("unused")
				boolean result = task.get(180, TimeUnit.SECONDS);
			}catch(TimeoutException e) {
				task.cancel(true);
				//�����޷������ر�TCP���ӣ���������Ӷ�������ʱ�Ƿ��Ͽ����ӡ�
				throw new Exception("transfer file timeout");
			}finally {
				executor.shutdown();
				if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
					executor.shutdownNow();
				}
			}
		} catch (Exception e) {
			logger.error(e.getMessage() + ":" + zipName);
			throw e;
		} 

		//��������֤�Ƿ���ܳɹ�
		String submitResult = getTcpClient().submit(sendDate + "," + zipName);
		if ("success".equals(submitResult)) {
			String successPath = getSuccessPath() + "/" + zipName;
			move(zipPath, successPath);
			logger.info("<SERVER_REPLY>"+ submitResult +"</SERVER_REPLY>");
		} else if ("incomplete".equals(submitResult)) {
			logger.error("<SERVER_REPLY>"+ submitResult +"</SERVER_REPLY>");
		} else {
			if ( Utils.isAK_FOURM_BAT(submitResult) ) {
				//�����ļ���
				new File(getFailPath() + "/duplication").mkdirs();
				String failPath = getFailPath() + "/duplication/" + zipName;
				//�Ƿ��Ѿ�����
				if ( new File(failPath).exists() ) {
					//ֱ��ɾ
					FileUtils.forceDelete(new File(zipPath));
				} else {
					//����
					move(zipPath,failPath);
				}
			} else {
				logger.error("<SERVER_REPLY>"+ submitResult +"</SERVER_REPLY>");
				if(!failflag) {
					String failPath = getFailPath() + "/" + zipName;
					move(zipPath,failPath);
				}
			}
		}
		logger.debug("transferAndMove()--> transer success: " + zipPath);
		return true;
	}	
	
	/**
	 * ��srcת����dest
	 * @param src Դ�ļ�ȫ·��
	 * @param dest Ŀ���ļ�ȫ·��
	 */
	public static void move(String src, String dest) {
		File sf = new File(src);
		File df = new File(dest);
		try {
			FileUtils.copyFile(sf, df);
			FileUtils.forceDelete(sf);
			logger.info("file move success:" + src + "->" + dest);
		} catch (IOException e) {
			logger.warn("file move fail:" + src + "->" + dest + "\n" + Utils.printStackTrace(e));
		}
	}
	
	/**
	 * ��֤�����ø�Ŀ¼�Ƿ���Ϲ���
	 */
	private void pathCheck() {
		if(!pathExistsAndDir(getSourcePath())) {
			throw new IllegalArgumentException("the param 'sourcePath' not exists or not a directory");
		}
		if(!pathExistsAndDir(getHistPath())) {
			throw new IllegalArgumentException("the param 'histPath' not exists or not a directory");
		}
		if(!pathExistsAndDir(getZipPath())) {
			throw new IllegalArgumentException("the param 'zipPath' not exists or not a directory");
		}
		if(!pathExistsAndDir(getSuccessPath())) {
			throw new IllegalArgumentException("the param 'successPath' not exists or not a directory");
		}
		if(!pathExistsAndDir(getFailPath())) {
			throw new IllegalArgumentException("the param 'failPath' not exists or not a directory");
		}
		
	}
	
	/**
	 * ��֤����·���Ƿ����
	 */
	public boolean pathExistsAndDir(String path) {
		File f = new File(path);
		if(!f.exists() || !f.isDirectory())	{
			return false;
		} else {
			return true;
		}
	}
	

	public String getSourcePath() {
		return sourcePath;
	}
	public void setSourcePath(String sourcePath) {
		this.sourcePath = sourcePath;
	}
	public String getHistPath() {
		return histPath;
	}
	public void setHistPath(String histPath) {
		this.histPath = histPath;
	}
	public String getZipPath() {
		return zipPath;
	}
	public void setZipPath(String zipPath) {
		this.zipPath = zipPath;
	}
	public String getSuccessPath() {
		return successPath;
	}
	public void setSuccessPath(String successPath) {
		this.successPath = successPath;
	}
	public String getFailPath() {
		return failPath;
	}
	public void setFailPath(String failPath) {
		this.failPath = failPath;
	}	
	public FourmFtpClient getFtpClient() {
		return ftpClient;
	}
	public void setFtpClient(FourmFtpClient ftpClient) {
		this.ftpClient = ftpClient;
	}
	public String getServerPath() {
		return serverPath;
	}
	public void setServerPath(String serverPath) {
		this.serverPath = serverPath;
	}
	public TcpClient getTcpClient() {
		return tcpClient;
	}
	public void setTcpClient(TcpClient tcpClient) {
		this.tcpClient = tcpClient;
	}
	public String getSuffix() {
		return suffix;
	}
	public void setSuffix(String suffix) {
		this.suffix = suffix;
	}
	public int getMaxLine() {
		return maxLine;
	}
	public void setMaxLine(int maxLine) {
		this.maxLine = maxLine;
	}
	public String getDateStr() {
		return fmt.format(new Date());
	} 
}
