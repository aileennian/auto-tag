package com.gh.svn;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;

/**
 * 每小时生成一个分支
 * 
 * @author admin
 *
 */
public class SvnProjectService {

	public enum TagType {
		java
	}

	// SVN的用户名、密码
	private String username = null;
	private String password = null;

	private String workspace = "";

	private String mainProjectSvnUrl = "";
	private String mainProjectSvnTagUrl = "";

	private String[] subProject = null;
	private String svnPojoUrl = "";
	private String svnParentUrl = "";// 相对路径
	private String subVersion = "";// 相以
	private String mvncmd = "";
	private final int sleep = 100;

	private String svnRoot = "";
	private String svnTagRoot = "";

	/**
	 * 路径格式除去
	 * 
	 * @param arg【】
	 *            [0] java [1] 需要打分支的主工程名， 路径去掉"svn.java.root"的后半部分数据 [2] parent
	 *            's path 相对主工的parent子项目 [3] pojo's path 相对主工的pojo子项目 [..]
	 *            相对路，主工程下的子工程的路径集合,只支持一级工程集
	 * @throws Exception
	 */
	public static void main(String[] arg) throws Exception {
		
		if (arg == null || arg.length < 2) {
			throw new RuntimeException("参数有误");
		}
		TagType tagType = Enum.valueOf(TagType.class, arg[0]);

		if (tagType != TagType.java) {
			throw new RuntimeException("第一个参数为java");
		}

		switch (tagType) {
		case java:
			String svnProjectUrl = arg[1];// gh-assemble";
			if (svnProjectUrl.lastIndexOf("/") == 0) {
				svnProjectUrl = svnProjectUrl.substring(0, svnProjectUrl.length() - 1);
			}

			String svnParenturl = arg[2];
			String svnPojoUrl = arg[3];
			
			String[] subProject = new String[0];
			if (arg.length > 4) {
				if (arg[4]=="all"){
					
				}else{
					subProject = new String[arg.length - 4];
					for (int i = 4; i < arg.length; i++) {
						subProject[i - 4] = arg[i];
					}
				}
			}
			SvnProjectService service = new SvnProjectService();
			service.initJava(tagType, svnProjectUrl, svnParenturl, svnPojoUrl, subProject);

			service.runJava();
			break;

		}

	}
	
	public static void log(String message) {
		System.out.println(message);
	}

	public void initJava(TagType tagType, String projectUrl, String svnParenturl, String svnPojoUrl,
			String... subProject) throws Exception {
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmm");// 设置日期格式
		subVersion = df.format(new Date());
		username = PropertiesUtil.getValue("svn.username");
		svnRoot = PropertiesUtil.getValue("svn.java.root");
		svnTagRoot = PropertiesUtil.getValue("svn.tag.root");

		password = PropertiesUtil.getValue("svn.password");
		workspace = PropertiesUtil.getValue("workspace.base") + "/" + projectUrl;
		
		
		if (subProject==null || subProject.length<=0){
			this.mainProjectSvnTagUrl = svnTagRoot + "/" + tagType.name() + "/" + projectUrl + "/" + this.subVersion;
		}else{
			this.mainProjectSvnTagUrl = svnTagRoot + "/" + tagType.name() + "/" + projectUrl + "/" + this.subVersion+"_"+subProject[0];
		}
		
		this.mainProjectSvnUrl = svnRoot + "/" + projectUrl;
		this.svnParentUrl = svnParenturl;
		this.svnPojoUrl = svnPojoUrl;
		this.subProject = subProject;

		/*
		 * mainProjectSvnTagUrl = mainProjectSvnTagUrl.replace("//", "/");
		 * mainProjectSvnUrl = mainProjectSvnUrl.replace("//", "/");
		 * svnParentUrl = svnParentUrl.replace("//", "/"); svnPojoUrl =
		 * svnPojoUrl.replace("//", "/"); workspace = workspace.replace("//",
		 * "/");
		 */
		mvncmd = PropertiesUtil.getValue("mvn");
		cleanWorkDirectory(workspace);
		
		execCmd("cd " + workspace, null);

		// String url = execCmd("ls -l ./ |awk '/^d/ {print $NF}'", null);
		// System.out.println("url="+url);
		// System.exit(0);

	}

	public void isExists(String svnUrl) throws Exception {
		String re = execCmd("svn list " + svnUrl + "  --username " + this.username + "  --password " + this.password,
				null);
		if (re!=null && (re.indexOf(" W160013")>-1 || re.indexOf("E200009")>-1)){
			
		}else{
			if (svnUrl.lastIndexOf(this.svnPojoUrl)>-1
					|| svnUrl.lastIndexOf(this.svnParentUrl)>-1
					){
				//execCmd("svn delete "+svnUrl + "  --username " + this.username + "  --password " + this.password + " -message 'commit delete'",new File(this.workspace));
				//execCmd("svn commit "+svnUrl + "  --username " + this.username + "  --password " + this.password + " -m 'commit delete'",new File(this.workspace));
			}else{
				log(svnUrl+"目录已存在，不能再建分支");
				System.exit(0);
			}
		}
	}

	public void runJava() throws Exception {
		if (this.subProject.length > 0) {
			//this.subProject[0]
			if (this.isNotBlank(svnPojoUrl)) {
				playTag(mainProjectSvnUrl + "/" + this.svnPojoUrl, mainProjectSvnTagUrl + "/" + this.svnPojoUrl);
			}
			if (this.isNotBlank(svnParentUrl)) {
				playTag(mainProjectSvnUrl + "/" + this.svnParentUrl, mainProjectSvnTagUrl + "/" + this.svnParentUrl);
			}

			for (int i = 0; i < subProject.length; i++) {
				String url = mainProjectSvnUrl + "/" + this.getProjectName(subProject[i]);
				String tagurl = mainProjectSvnTagUrl + "/" + this.getProjectName(subProject[i]);
				playTag(url, tagurl);
			}

		} else {
			
			playTag(mainProjectSvnUrl, mainProjectSvnTagUrl);

		}

		fetchSVN(mainProjectSvnTagUrl, this.workspace);

		/**
		 * 编辑pojo的xml
		 */
		String pojoVersion = setPojoVersion();

		Thread.currentThread();
		Thread.sleep(sleep);
		;
		String parentVersion = setParentVersion(pojoVersion);
		Thread.currentThread();
		Thread.sleep(sleep);
		;

		String parentRelativePath = "../" + this.svnParentUrl + "/pom.xml";
		if (this.subProject.length > 0) {
			for (int i = 0; i < subProject.length; i++) {
				String path = this.workspace + "/" + subProject[i];
				configVersion(path, parentVersion, parentRelativePath);
			}
			// 生成集合pom.xml
			//createPomXml(this.subProject, workspace);
		} else {
			File dir = new File(this.workspace);
			File[] file = dir.listFiles();
			for (int i = 0; i < file.length; i++) {
				if (file[i].isDirectory()) {
					String name = file[i].getName();
					if (name.indexOf(this.svnPojoUrl) > -1 || name.indexOf(this.svnParentUrl) > -1
							|| name.indexOf(".svn") > -1) {
						continue;
					}
					String path = this.workspace + "/" + name;
					log(path + ",parentVersion=" + parentVersion + ",parentRelativePath=" + parentRelativePath);
					configVersion(path, parentVersion, parentRelativePath);
				}
			}
		}
		Thread.currentThread();
		Thread.sleep(sleep);

		
		String mvnLocalRepository = PropertiesUtil.getValue("mvm.local.repository");
		this.execCmd("chown -R jenkins:jenkins "+mvnLocalRepository, null);
		this.execCmd("chmod -R 755 "+mvnLocalRepository, null);
		
		
		log("\t\n版本号:" + this.mainProjectSvnTagUrl + ",子版本号:" + subVersion);

		
		log("\t\n分支:" + this.mainProjectSvnTagUrl + ",子版本号:" + subVersion);

		
		System.exit(0);
	}

	/**
	 * check分支
	 * 
	 * @param svnUrl
	 * @param targetDir：检出文件夹名
	 * @throws Exception
	 */
	private void fetchSVN(String svnUrl, String targetDir) throws Exception {
		File workspaceDir = new File(workspace);

		String cmd = "svn  checkout " + svnUrl + "  " + targetDir + " --username " + this.username + " --password "
				+ this.password + " --force --depth infinity -q";

		execCmd(cmd, workspaceDir);
		execCmd("cd " + targetDir, workspaceDir);
		execCmd("svn cleanup", new File(targetDir));
		Thread.currentThread();
		Thread.sleep(sleep);
		;
	}

	private static void writeToNewXMLDocument(Document document, String xmlFile) throws IOException {
		OutputFormat format = OutputFormat.createPrettyPrint();
		format.setEncoding("UTF-8");
		XMLWriter writer = new XMLWriter(new FileWriter(xmlFile), format);
		// XMLWriter writer = new XMLWriter(new FileWriter(xmlFile));
		writer.write(document);
		writer.close();
	}

	private String getProjectName(String svnurl) {
		String url = svnurl;
		if (url.lastIndexOf("/") == 0) {
			url = url.substring(0, url.length() - 1);
		}

		if (url.lastIndexOf("/") > -1) {
			url = url.substring(url.lastIndexOf("/") + 1);
			return url;
		} else {
			return url;
		}

	}

	@SuppressWarnings("unchecked")
	private String setPojoVersion() throws Exception {
		if (this.isBlank(this.svnPojoUrl)) {
			return "";
		}

		// String svnurl = this.mainProjectSvnTagUrl+"/" + this.;
		//String projectName = this.getProjectName(svnPojoUrl);
		String dir = this.workspace + "/" + svnPojoUrl;
		
		SAXReader reader = new SAXReader();
		String xmlFile = dir + "/pom.xml";
		log("\t\n 操作pom.xml文件:" + xmlFile);

		// 得到版本号
		Document document = reader.read(xmlFile);
		Element root = document.getRootElement();

		Element versionElement = root.element("version");
		String version = "";
		if (versionElement != null) {
			version = versionElement.getText();
		}
		version = formatVersion(version, subVersion);

		/**
		 * 置入新牒一号
		 */
		// Element versionElement = root.element("/version");
		if (versionElement == null) {
			Element elem = root.addElement("version");
			elem.setText(version);
		} else {
			versionElement.setText(version);
		}
		writeToNewXMLDocument(document, xmlFile);

		log("\t\n pom.xml文件修改完比");

		File targetDir = new File(dir);
		execCmd("cd " + dir, targetDir);
		// process.waitFor();
		log("\t\n 开始maven打包 编译");
		//execCmd(mvncmd + " clean install deploy -Dmaven.test.skip=true", targetDir);
		execCmd(mvncmd + " clean deploy -Dmaven.test.skip=true", targetDir);

		execCmd("cd " + dir, null);
		execCmd("svn commit -m '更新版本号' --username " + this.username + " --password " + this.password
				+ " --depth infinity", targetDir);

		Thread.currentThread();
		Thread.sleep(sleep);
		;
		log("\t\n 编译结束 \n\t");
		return version;
	}

	@SuppressWarnings("unchecked")
	private String setParentVersion(String parentVersion) throws Exception {
		if (this.isBlank(this.svnParentUrl)) {
			return "";
		}
		String dir = this.workspace + "/" + svnParentUrl;

		SAXReader reader = new SAXReader();
		String xmlFile = dir + "/pom.xml";
		log("\t\n 操作pom.xml文件:" + xmlFile);
		// 得到版本号
		Document document = reader.read(xmlFile);
		Element root = document.getRootElement();

		Element versionElement = root.element("version");
		String version = "";
		if (versionElement != null) {
			version = versionElement.getText();
		}

		version = formatVersion(version, subVersion);

		/**
		 * 置入新牒一号
		 */
		// Element versionElement = root.element("/version");
		if (versionElement == null) {
			Element elem = root.addElement("version");
			elem.setText(version);
		} else {
			versionElement.setText(version);
		}

		if (isNotBlank(parentVersion)) {
			Element propertiesElement = root.element("properties");
			Element pojoVersionElement = propertiesElement.element("pojo-base.version");
			if (pojoVersionElement != null)
				pojoVersionElement.setText(parentVersion);
		}
		
		writeToNewXMLDocument(document, xmlFile);

		log("\t\n pom.xml文件修改完比");

		File targetDir = new File(dir);
		execCmd("cd " + dir, targetDir);
		log("\t\n 开始maven打包 编译");
		execCmd(mvncmd + " clean deploy -Dmaven.test.skip=true", targetDir);

		// Thread.currentThread();Thread.sleep(sleep);;
		log("\t\n 将变更上传至svndir:" + dir);
		execCmd("cd " + dir, null);
		execCmd("svn commit -m '更新版本号' --username " + this.username + " --password " + this.password
				+ " --depth infinity", targetDir);

		Thread.currentThread();
		Thread.sleep(sleep);
		;
		log("\t\n 编译结束 \n\t");
		return version;
	}

	/**
	 * 
	 * @param svnurl：url
	 * @param isEditParentVersion：是否修改父亲节眯的版本
	 * @param pojoBaseVersion：pojo版本号
	 * @param parentVersion:父版梧与
	 * @param deploy:是否发布到私服
	 * @param isCommitUpdate：是否提交更新
	 * @return
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	private void configVersion(String subProjectDir, String parentVersion, String parentRelativePath) throws Exception {

		SAXReader reader = new SAXReader();
		String xmlFile = subProjectDir + "/pom.xml";
		log("\t\n 操作pom.xml文件:" + xmlFile);
		// 得到版本号
		Document document = reader.read(xmlFile);
		Element root = document.getRootElement();

		Element parentElement = root.element("parent");
		Element parentVersionElement = null;
		Element relativePathElement = null;
		if (parentElement != null) {
			Element parentGroupIdElement = parentElement.element("groupId");
			Element artifactIdElement = parentElement.element("artifactId");
			if (parentGroupIdElement!=null 
					&& artifactIdElement!=null
					&& !parentGroupIdElement.getText().equals("com.gh")
					&& !artifactIdElement.getText().equals("gh-parent")
					){				
				parentVersionElement = parentElement.element("version");
				// 置入父版本号
				if (this.isNotBlank(parentVersion)) {
					parentVersionElement.setText(parentVersion);
				}
				relativePathElement = parentElement.element("relativePath");
				if (isNotBlank(parentRelativePath)) {
					if (relativePathElement == null) {
						relativePathElement = parentElement.addElement("relativePath");
					}
					relativePathElement.setText(parentRelativePath);
				}
			
			}
		}
		writeToNewXMLDocument(document, xmlFile);

		log("\t\n pom.xml文件修改完比");

		File targetDir = new File(subProjectDir);
		execCmd("cd " + subProjectDir, null);

		log("\t\n 开始maven打包 编译");
		execCmd(mvncmd + " clean package -Dmaven.test.skip=true", targetDir);

		Thread.sleep(sleep);
		execCmd("cd " + subProjectDir, null);

		execCmd("svn commit -m '更新版本号' --username " + this.username + " --password " + this.password
				+ " --depth infinity", targetDir);

		Thread.currentThread();
		Thread.sleep(sleep);

		log("\t\n 编译结束 \n\t");
	}

	public void testXml() throws DocumentException {
		SAXReader reader = new SAXReader();
		String xmlFile = "D:\\gonghui\\sourcecode\\java\\gh-assemble\\pojo-base\\pom.xml";
		// 得到版本号
		Document document = reader.read(xmlFile);
		Element root = document.getRootElement();
		// Element elem = root.addElement("version");
		// log("root="+root.getName());
		Element element = root.element("parent");

		Element element1 = root.element("version");

		// Node selectNodes = document.selectSingleNode("project/version");
		log(element.getText());

	}

	private String formatVersion(String version, String subVersion) {
		if (version.indexOf(subVersion) > -1) {
			return version;
		}
		version = version.trim();
		if (version.indexOf("SNAPSHOT") > -1) {
			version = version.substring(0, version.indexOf("SNAPSHOT") - 1);
			version += "-" + subVersion + "-SNAPSHOT";
		} else {
			version += "-" + subVersion;
		}
		return version;
	}

	/**
	 * 
	 * @param svnProjectUrl
	 *            https://192.168.3.192/svn/sourcecode/java/gh-assemble/web-company
	 * @param svnProjectTagUrl
	 * @param projectVersion
	 * @param singleProjectName:单独的工程名称，（作为tag名称一部分
	 * @return 返回tag分支的信息
	 * @throws Exception
	 */
	private String playTag(String fullProjectSvnUrl, String fullProjectTagSvnUrl) throws Exception {
		log("\t\n---------------------------------创建tag--------------------------------------------");
		
		isExists(fullProjectTagSvnUrl);
		
		File workDir = new File(workspace);
		String tagcmd = "svn copy " + fullProjectSvnUrl + "  " + fullProjectTagSvnUrl + " --parents --username "
				+ this.username + "  --password " + this.password + " --message '创建tag'";
		execCmd(tagcmd, workDir);
		Thread.currentThread().sleep(100);
		return fullProjectTagSvnUrl;
	}

	public void cleanWorkDirectory(String dir) throws Exception {
		String tagcmd = "rm -rf " + dir;
		execCmd(tagcmd, null);
		execCmd("mkdir -p " + this.workspace, null);
	}

	private boolean isNotBlank(String v) {
		if (v == null) {
			return false;
		}
		if (v.trim().equals("")) {
			return false;
		}

		return true;
	}

	private boolean isBlank(String v) {
		if (v == null || v.trim().equals("")) {
			return true;
		}
		return false;
	}

	/**
	 * 执行系统命令, 返回执行结果
	 *
	 * @param cmd
	 *            需要执行的命令
	 * @param dir
	 *            执行命令的子进程的工作目录, null 表示和当前主进程工作目录相同
	 */
	public String execCmd(String cmd, File dir) throws Exception {
		log("-------------------------------------------------------------------------");
		if (dir != null) {
			log(cmd + "【run in:" + dir + "】");
		} else {
			log(cmd);
		}

		StringBuilder result = new StringBuilder();
		Process process = null;
		BufferedReader bufrIn = null;
		BufferedReader bufrError = null;

		/** 指令执行结果 */
		StringBuffer queryInputResult = new StringBuffer(100);
		/** 输出错误的结果 */
		StringBuffer queryErroInputResult = new StringBuffer(100);

		try {
			// 执行命令, 返回一个子进程对象（命令在子进程中执行）
			process = Runtime.getRuntime().exec(cmd, null, dir);
			// 方法阻塞, 等待命令执行完成（成功会返回0）
			// process.waitFor();
			// 获取命令执行结果, 有两个结果: 正常的输出 和 错误的输出（PS: 子进程的输出就是主进程的输入）
			CountDownLatch lock = new CountDownLatch(2);
			// 处理正常的输入流

			ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2 * 2, 200, TimeUnit.MILLISECONDS,
					new ArrayBlockingQueue<Runnable>(2));

			executor.execute(new MyCheckTask(queryInputResult, lock, process.getInputStream()));
			// 处理error流
			executor.execute(new MyCheckTask(queryErroInputResult, lock, process.getErrorStream()));

			boolean done = false;
			while (!done) {
				try {
					lock.await();
					done = true;
				} catch (InterruptedException e) {
					// loop to wait
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		} finally {
			closeStream(bufrIn);
			closeStream(bufrError);

			// 销毁子进程
			if (process != null) {
				process.destroy();
			}
		}

		
		result.append("[ERROR]:"+queryErroInputResult);
		result.append("[SUCCESS]:"+queryInputResult);		
		
		return result.toString();
	}

	// 执行
	private String playRunTime(String cmd, File dir) throws Exception {
		StringBuffer result = new StringBuffer("");
		Process p = null;
		InputStream is = null;
		BufferedReader reader =null;
		try {
			p = Runtime.getRuntime().exec(cmd, null, dir);
			BufferedReader bf = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String line = null;
			while ((line = bf.readLine()) != null && line.length() > 0) {
				result.append(line + "\n");
			}
			p.waitFor();
		} finally {
			if (is!=null) is.close();
			if (reader!=null) reader.close();
			if (p!=null) p.destroy();
		}
		return result.toString();
	}

	private static void closeStream(Closeable stream) {
		if (stream != null) {
			try {
				stream.close();
			} catch (Exception e) {
				// nothing
			}
		}
	}

	private void createPomXml(String subproject[], String workDir) throws Exception {
		StringBuffer pom = new StringBuffer("");
		pom.append(
				"<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"");
		pom.append(
				"xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">");
		pom.append("<modelVersion>4.0.0</modelVersion>");
		pom.append("<artifactId>assemble-root</artifactId>");
		pom.append("<groupId>com.gh.assemble</groupId>");
		pom.append("<version>1.0.1-SNAPSHOT</version>");
		pom.append("<packaging>pom</packaging>");
		pom.append("<modules>");
			File dir = new File(this.workspace);
			File[] file = dir.listFiles();
			for (int i = 0; i < file.length; i++) {
				String name = file[i].getName();
				if (name.indexOf(this.svnPojoUrl) > -1 || name.indexOf(this.svnParentUrl) > -1
						|| name.indexOf(".svn") > -1) {
					continue;
				}
				pom.append("<module>" + file[i].getName() + "</module>");
			}
				pom.append("<module>" + this.svnPojoUrl + "</module>");
				pom.append("<module>" + this.svnParentUrl + "</module>");
		pom.append("</modules>");
		pom.append("</project>");

		
		String xmlFile = workDir + "/pom.xml";
		File myFile = new File(xmlFile);
		if (myFile.exists()){
			myFile.delete();
			saveToPomXML(pom.toString(), xmlFile);	
		}else{
			myFile.createNewFile();
			saveToPomXML(pom.toString(), xmlFile);		
			execCmd("svn add pom.xml", new File(workDir));
		}	
		execCmd("cd " + workDir, null);
		execCmd("svn commit -m 'add pom.xml'", new File(workDir));
	}

	public void saveToPomXML(String str, String xmlFile) throws IOException {
		SAXReader saxReader = new SAXReader();
		Document document;
		try {
			document = saxReader.read(new ByteArrayInputStream(str.getBytes()));
			Element rootElement = document.getRootElement();

			String getXMLEncoding = document.getXMLEncoding();
			String rootname = rootElement.getName();
			System.out.println("getXMLEncoding>>>" + getXMLEncoding + ",rootname>>>" + rootname);

			OutputFormat format = OutputFormat.createPrettyPrint();
			/** 指定XML字符集编码 */
			format.setEncoding("UTF-8");
			/** 将document中的内容写入文件中 */
			XMLWriter writer = new XMLWriter(new FileWriter(new File(xmlFile)), format);
			writer.write(document);
			writer.close();

		} catch (DocumentException e) {
			// TODOAuto-generatedcatchblock
			e.printStackTrace();
		}

	}

}
