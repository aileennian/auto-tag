package com.gh.svn;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.CountDownLatch;

/**
 * 内部类: 用来监控执行的流
 * 
 * @author jiajia.lijj
 * @version $Id: RuntimeExcTransport.java, v 0.1 2012-5-1 下午03:42:21
 *          Administrator Exp $
 */
public class MyCheckTask implements Runnable {

	/** 锁 */
	private CountDownLatch lock;

	/** 执行结果输入流 */
	private InputStream inputStream;

	/** 字符拼接 */
	private StringBuffer queryInputResult;

	public MyCheckTask(StringBuffer queryInputResult, CountDownLatch lock,InputStream inputStream) {
        super();
        this.lock = lock;
        this.inputStream = inputStream;
        this.queryInputResult = queryInputResult;
    }

	public void run() {
		try {
			BufferedReader bf = new BufferedReader(new InputStreamReader(inputStream));
			String line = null;
			while ((line = bf.readLine()) != null && line.length() > 0) {
				System.out.println(line);
				queryInputResult.append(line).append("\n");
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			lock.countDown();
		}
	}
}