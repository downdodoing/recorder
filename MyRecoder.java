package com.example.tools.myDefineRecoder;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;

@SuppressLint("SimpleDateFormat")
public class MyRecoder {
	@SuppressWarnings("deprecation")
	private int channelConfiguration = AudioFormat.CHANNEL_CONFIGURATION_MONO;
	private int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;// �����ֶȣ�һ��������16���أ������2���ֽ�
	private int audioSource = MediaRecorder.AudioSource.MIC;// ��ԴΪ��˷�
	private int frequency = 11025;// �����ʣ�ÿ��11025��������

	private AudioRecord audioRecord = null;
	private boolean isRecord;// ���ڼ�¼�Ƿ�����¼��
	private int bufferSize;// ���ڼ�¼��С��¼�������С

	private File file;// ���ڼ�¼�洢��¼���ļ�

	private TextView show_time;// ��ʾ¼��ʱ��
	private TextView show_percent;
	private ProgressBar pgb;
	private MyHandler handler = new MyHandler();
	// ���ڱ�ʾ¼��״̬
	private String state = "";
	// ���ڼ�¼¼������
	private int timeCount;
	// ���ڼ�¼���ŵ�ʱ��
	private int time;

	public MyRecoder(TextView show_time, ProgressBar pgb, TextView show_percent) {
		this.show_time = show_time;
		this.pgb = pgb;
		this.show_percent = show_percent;
		// ���ڼ�¼¼��ʱ��
		Thread t = new Thread(new HandleInvocation());
		t.start();
	}

	@SuppressLint("SimpleDateFormat")
	private void initVoiceRecode() {

		// ��ʼ��buffer�Լ�audioRecord
		bufferSize = AudioRecord.getMinBufferSize(frequency,
				channelConfiguration, audioEncoding);
		audioRecord = new AudioRecord(audioSource, frequency,
				channelConfiguration, audioEncoding, bufferSize);
	}

	// �����ļ�
	public void createFile() {
		// ��ȡ��ǰʱ��
		SimpleDateFormat formate = new SimpleDateFormat("yyyy��MM��dd��HHʱmm��ss��");
		Date date = new Date(System.currentTimeMillis());
		String currentTime = formate.format(date);

		if (Environment.getExternalStorageState().equals(
				Environment.MEDIA_MOUNTED)) {
			File directory = new File(Environment.getExternalStorageDirectory()
					.getAbsoluteFile() + "/voice");

			directory.mkdirs();

			file = new File(directory.getAbsoluteFile() + "/" + currentTime
					+ ".pcm");
			if (!file.exists()) {
				try {
					file.createNewFile();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		} else {
			Log.i("��ʾ", "����洢��");
		}
	}

	public void start() {
		timeCount = 0;
		// ֻ����һ��¼���ļ�
		if (file != null) {
			file.delete();
		}
		state = "recording";
		isRecord = true;// ��ʾ����¼��
		VoiceRecordTask task = new VoiceRecordTask();
		task.execute();
	}

	// ��ͣ
	public void pause() {
		state = "pause";
		isRecord = false;
	}

	public void stop() {
		state = "stop";
		isRecord = false;
	}

	// ����¼��
	public void continuerRecord() {
		isRecord = true;
		new VoiceRecordTask().execute();
	}

	public void play() {
		if (file != null) {
			int voiceLength = (int) (file.length() / 2);
			short[] music = new short[voiceLength];

			try {
				DataInputStream dataInputStream = new DataInputStream(
						new BufferedInputStream(new FileInputStream(file)));

				for (int i = 0; dataInputStream.available() > 0; i++) {
					music[i] = dataInputStream.readShort();
				}
				// �ر�������
				dataInputStream.close();
				AudioTrack audioTrack = new AudioTrack(
						AudioManager.STREAM_MUSIC, frequency,
						channelConfiguration, audioEncoding, voiceLength * 2,
						AudioTrack.MODE_STREAM);
				audioTrack.write(music, 0, voiceLength);
				// ��ʼ����
				audioTrack.play();
				// write the music buffer to the AudioTrack object
				audioTrack.stop();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				Log.e("AudioTrack", "Playback Failed");
				e.printStackTrace();
			}
			pgb.setMax(timeCount);
			Thread t = new Thread(new ��andlePlay());
			t.start();
		}
	}

	class ��andlePlay implements Runnable {

		@Override
		public void run() {
			for (time = 1; time <= timeCount; time++) {
				pgb.setProgress(time);
				Message msg = new Message();
				msg.what = 1;
				handler.sendMessage(msg);
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	class VoiceRecordTask extends AsyncTask<Void, Void, Void> {
		// ���ڸ÷�������һЩ׼��
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
		}

		// �÷�������ʵ��
		@Override
		protected Void doInBackground(Void... params) {
			// ���start��ͻ����´����ļ�����¼������
			if (state.equals("recording")) {
				createFile();
			}
			if (null == audioRecord) {
				initVoiceRecode();
			}
			try {
				// �������ļ���׷���ļ�����
				RandomAccessFile random = new RandomAccessFile(file, "rw");
				short[] shortArray = new short[bufferSize / 4];
				// ��ʼ¼����Ƶ
				audioRecord.startRecording();

				while (isRecord) {
					int bufferResult = audioRecord.read(shortArray, 0,
							bufferSize);
					// ����д�ļ�ָ���ƶ���ĩβ
					random.seek(random.length());
					for (int i = 0; i < bufferResult; i++) {
						random.writeShort(shortArray[i]);
					}
				}

				// ֹͣ¼��
				if (null != audioRecord)
					audioRecord.stop();
				random.close();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}

			return null;
		}

		// �ڵ���publishProgress�����
		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);
		}

		// �����ڴ���doInBackground���صĽ��
		@Override
		protected void onProgressUpdate(Void... values) {
			// TODO Auto-generated method stub
			super.onProgressUpdate(values);
		}

	}

	@SuppressLint("HandlerLeak")
	class MyHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			if (0 == msg.what) {
				int second = timeCount % 60;
				int minute = timeCount / 60;

				String sec = second > 9 ? second + "" : "0" + second;
				String min = minute > 9 ? minute + "" : "0" + minute;

				show_time.setText(min + ":" + sec);
			} else if (1 == msg.what) {
				show_percent
						.setText((int) ((time * 1.0 / timeCount * 1.0) * 100)
								+ "%");
			}
		}
	}

	class HandleInvocation implements Runnable {

		@Override
		public void run() {
			while (true) {
				if (isRecord) {
					Message msg = new Message();
					msg.what = 0;
					handler.sendMessage(msg);
					timeCount++;
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}

	}
}
