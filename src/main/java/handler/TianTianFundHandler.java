package handler;

import com.google.gson.Gson;
import bean.FundBean;
import com.intellij.ide.util.PropertiesComponent;
import utils.HttpClientPool;
import utils.LogUtil;
import utils.WindowUtils;

import javax.swing.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class TianTianFundHandler extends FundRefreshHandler {
    public final static DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static Gson gson = new Gson();
    private final List<String> codes = new ArrayList<>();

    private Thread worker;
    private JLabel refreshTimeLabel;
    /**
     * 更新数据的间隔时间（秒）
     */
    private volatile int threadSleepTime = 60;

    public TianTianFundHandler(JTable table, JLabel refreshTimeLabel) {
        super(table);
        this.refreshTimeLabel = refreshTimeLabel;
    }

    @Override
    public void handle(List<String> code) {
        if (worker!=null){
            worker.interrupt();
        }
        LogUtil.info("Leeks 更新Fund编码数据.");
        if (code.isEmpty()){
            return;
        }

        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                while (worker!=null && worker.hashCode() == Thread.currentThread().hashCode() && !worker.isInterrupted()){
                    synchronized (codes){
                        stepAction();
                    }
                    try {
                        Thread.sleep(threadSleepTime * 1000);
                    } catch (Exception e) {
                        LogUtil.info("Leeks 已停止更新Fund编码数据."+e);
                        refreshTimeLabel.setText("stop");
                        return;
                    }
                }
            }
        });
        synchronized (codes){
            codes.clear();
            codes.addAll(code);
        }
        worker.start();
    }

    @Override
    public void stopHandle() {
        if (worker != null) {
            worker.interrupt();
            LogUtil.info("Leeks 准备停止更新Fund编码数据.");
        }
    }

    private void stepAction(){
//        LogUtil.info("Leeks 刷新基金数据.");
        for (String s : codes) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        String result = HttpClientPool.getHttpClient().get("http://fundgz.1234567.com.cn/js/"+s.split(":")[0]+".js?rt="+System.currentTimeMillis());
                        String json = result.substring(8,result.length()-2);
                        if(!json.isEmpty()){
                            FundBean bean = gson.fromJson(json,FundBean.class);
                            bean.setHoldPrice(s.split(":")[1]);
                            NumberFormat numberFormat = NumberFormat.getInstance();
                            numberFormat.setMaximumFractionDigits(2);
                            String format = numberFormat.format(((Double.parseDouble(bean.getDwjz()) - Double.parseDouble(bean.getHoldPrice()))/ Double.parseDouble(bean.getHoldPrice()))*100)+"%";
                            bean.setYieldRate(format);
                            updateData(bean);
                        }else {
                            LogUtil.info("Fund编码:["+s+"]无法获取数据");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }
        updateUI();
    }
    public void updateUI() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                refreshTimeLabel.setText("最后刷新时间:"+LocalDateTime.now().format(timeFormatter));
                refreshTimeLabel.setToolTipText("最后刷新时间，刷新间隔" + threadSleepTime + "秒");
            }
        });
    }

    public int getThreadSleepTime() {
        return threadSleepTime;
    }

    public void setThreadSleepTime(int threadSleepTime) {
        this.threadSleepTime = threadSleepTime;
    }
}
