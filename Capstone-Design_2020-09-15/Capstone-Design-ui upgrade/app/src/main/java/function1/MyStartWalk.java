package function1;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.Volley;
import com.example.myregisterlogin.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import function1.function4.BadDogThread;
import function1.function4.MyNullLocationRequest;
import function1.walktimecalculater.UserInform;
import function1.walktimecalculater.WalkTimeCalculater;
import main.MainActivity;
import myinformation.MyInformChange;
import myinformation.MyInformChangeRequest;
import myinformation.MySettings;
import registerlogin.LoginActivity;
import shareddata.PreferenceManager;

public class MyStartWalk extends AppCompatActivity {

    //버튼 정의
    private ImageButton btn_cancle;
    private ImageButton imgbtn_play;
    private ImageButton imgbtn_stop;
    private Button btn_finish;

    //프로그레스바
    private ProgressBar progressBar;

    //흘러가는 산책 시간
    Integer new_walkMinute;

    //프로그레스바에 이용 progressBar
    //추천 산책 시간
    Integer recoWalkMinuteForPro =0;
    float progressPercent = 0;
//  progressPercent = ( new_walkMinute /recoWalkMinuteForPro)*100 ; 어야함

    //스톱워치
    private Thread timeThread = null;
    private Boolean isRunning = true;

    //기피견종 알림 기능 스레드 관련
    private Thread badDogThread = null;


    String userID;

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_start_walk);

        //산책시작메인 화면에서 넘어온 아이디 정보 받기
        Intent intent = getIntent();
        userID= intent.getStringExtra("userID");

        //유아이 찾기
        btn_cancle = (ImageButton) findViewById(R.id.btn_cancle);
        imgbtn_play = (ImageButton) findViewById(R.id.imgbtn_play);
        imgbtn_stop = (ImageButton) findViewById(R.id.imgbtn_stop);
        btn_finish = (Button) findViewById(R.id.btn_finish);
        //
        progressBar = (ProgressBar) findViewById(R.id.progressBar);

        //버튼 보이고 안보이고 설정
        imgbtn_stop.setVisibility(View.GONE);
        imgbtn_play.setVisibility(View.VISIBLE);

        //추천 산책시간 계산하여 tv_recowalktime에 반영하기 //  + 추천 산책시간 받아옴
        set_tv_recowalktime();

        // 스톱워치 기능
        timeThread = new Thread(new TimeThread());

        //20200915
        if((PreferenceManager.getString(MyStartWalk.this,"badDogAlarm"+userID)).equals("true")){ //true면
            // 기피견종 알림 기능 (멈춰있을 때도 작동)
            badDogThread = new Thread((new BadDogThread(MyStartWalk.this, userID)));
            badDogThread.start();
        }
        else{
            //기피 견종 알림 기능을 켜시겠습니까?
            new android.app.AlertDialog.Builder(MyStartWalk.this)
                    .setTitle("알림").setMessage("기피 견종 알림을 켜시겠습니까 ?")
                    .setPositiveButton("네", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            PreferenceManager.setString(MyStartWalk.this, "badDogAlarm"+userID,"true");
                            // 기피견종 알림 기능 (멈춰있을 때도 작동)
                            badDogThread = new Thread((new BadDogThread(MyStartWalk.this, userID)));
                            badDogThread.start();
                        }
                    })
                    .setNegativeButton("아니오", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                        }
                    })
                    .show();

        }


        //산책 취소 버튼
        btn_cancle.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {

                new AlertDialog.Builder(MyStartWalk.this)
                        .setTitle("산책 취소").setMessage("정말 취소 하시겠습니까?")
                        .setPositiveButton("산책 취소", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
//                                onBackPressed(); //취소버튼 누른거랑 동일한 효과
                                //사용자 위도 경도 null 만듦
                                makeUserLocationNull(userID);

                                //20200915
                                //시간 스탑
                                badDogThread.interrupt();

                                Intent i = new Intent(MyStartWalk.this, MyStartWalkMain.class);
                                i.putExtra("userID", userID);
                                i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                startActivity(i);

                            }
                        })
                        .setNegativeButton("취소", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {

                            }
                        })
                        .show();

            }
        });

        //시작 누르면 자동으로 산책 타이머 시작해버리기
        playWalkTime();

        //산책 재생 버튼
        imgbtn_play.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {

                //산책한 산책시간 흘러가게 하기
                playWalkTime();


            }
        });
        //산책 일시정지 버튼
        imgbtn_stop.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                stopWalkTime();
            }
        });

        //20200915
        //산책 종료 버튼
        //산책 결과 파일에 저장 +  사용자 위도 경도 null 만듦
        btn_finish.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {


                new AlertDialog.Builder(MyStartWalk.this)
                        .setTitle("산책 종료").setMessage("산책을 저장 하시겠습니까?")
                        .setPositiveButton("저장하기", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {

                                //산책한 시간 파일에 저장
                                saveWalkTime(new_walkMinute);

                                //시간 스탑
                                badDogThread.interrupt();

                                //사용자 위도 경도 null 만듦
                                makeUserLocationNull(userID);


                                //만족도 조사할지 말지를 물어봄
                                new AlertDialog.Builder(MyStartWalk.this)
                                        .setTitle("만족도 조사").setMessage("만족도 평가를 하시겠습니까?")
                                        //예 : 만족도 조사 화면으로
                                        .setPositiveButton("예", new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int whichButton) {
                                                Intent i = new Intent(MyStartWalk.this, PopupActivity.class);
                                                i.putExtra("userID", userID);
                                                i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                                startActivity(i);
                                            }
                                        })
                                        //아니요 : 메인화면으로
                                        .setNegativeButton("나중에 할래요", new DialogInterface.OnClickListener() {
                                                    public void onClick(DialogInterface dialog, int whichButton) {
                                                        Intent i = new Intent(MyStartWalk.this, MainActivity.class);
                                                        i.putExtra("userID", userID);
                                                        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                                        startActivity(i);
                                            }
                                        })
                                        .show();
                            }
                        })
                        //산책 저장 취소
                        .setNegativeButton("취소", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {

                            }
                        })
                        .show();

            }
        });


    }
    //사용자의 위도와 경도 값 null 만들기
    //강종하거나 취소하고 나갔을시에도 null 만들어야함
    public void makeUserLocationNull(String userID){
        //불리 구문
        Response.Listener<String> responseListner = new Response.Listener<String>(){
            @Override
            public void onResponse(String response) {

                //제이슨 오브젝트 로 서버 전송 으로 사용자 위치 정보 등록함 (일반 String 사용할수 없기때문) = 운반체
                try
                {
                    JSONObject jsonObject = new JSONObject(response); //알트+엔터로 오류 처리
                    boolean success = jsonObject.getBoolean("success"); //php문에서 success 값을 가져옴 성공여부 알수 있음
                    if (success)
                    {
                        Toast.makeText(getApplicationContext(), "내 위치 정보 초기화 성공 ", Toast.LENGTH_LONG).show();

                    } else
                    {    //등록 실패
                        Toast.makeText(getApplicationContext(), "내 위치 정보 초기화 실패 ", Toast.LENGTH_LONG).show();
                        return;
                    }

                } catch (
                        JSONException e)
                {
                    e.printStackTrace();
                }

            }
        };

        //20200915
        Double myLatitude, myLongitude;
        myLatitude = null;
        myLongitude = null;

        //20200915
        //서버로 볼리를 이용해서 (레지스터 리퀘스트) 요청을 함
        MyNullLocationRequest myNullLocationRequest = new MyNullLocationRequest(userID,myLatitude,myLongitude,responseListner);
        RequestQueue queue = Volley.newRequestQueue(MyStartWalk.this);
        queue.add(myNullLocationRequest);


    }



    //20200915
    // (스레드) db테이블에서 거리 10m 이내 사용자들의 반려견 사이즈종 = 내 기피견종 인 사용자들 있으면 경고표시
//    ArrayList otherDogSizeList = new ArrayList();


    //산책시간 측정 스톱워치 관련 (실시간 작동)
    @SuppressLint("HandlerLeak")
    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            int sec = (msg.arg1 / 100) % 60; //초
            int min = (msg.arg1 / 100) / 60; //분
            int hour = (msg.arg1 / 100) / 360; //시간 (시)
            //1000이 1초 1000*60 은 1분 1000*60*10은 10분 1000*60*60은 한시간

            @SuppressLint("DefaultLocale") String result = String.format("%02d 시간 %02d 분 %02d 초", hour, min, sec);
            set_tv_walktime(result);

            //시간 저장
            //시, 분, 초 나누지 않고 /100만 함
            new_walkMinute = msg.arg1 /100 ; //초
            progressPercent = ( (float) new_walkMinute / (float) recoWalkMinuteForPro )*100;
            progressBar.setProgress((int) progressPercent);

        }

    };

    //추천 산책시간 계산하여 tv_recowalktime에 반영하기
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    void set_tv_recowalktime(){

        //개 종류에 맞게 산책 시간 설정하기
        TextView tv_recowalktime = (TextView) findViewById(R.id.tv_recowalktime);

        UserInform userInform;


        //산책시간, 산책 횟수 조정하기
        Integer recoWalkTime = 0;
        Integer recoWalkMinute = 0;
        WalkTimeCalculater walkTimeCalculater;

        walkTimeCalculater = new WalkTimeCalculater();

        //만족도 결과 파일이 있을 경우
        File saveFile = new File(getFilesDir() , "/userData"); // 저장 경로
        //폴더생성
        if(!saveFile.exists()){ // 폴더 없을 경우
            saveFile.mkdir(); // 폴더 생성
        }

        recoWalkTime = walkTimeCalculater.WalkTimeCalculater(userID, saveFile); //추천 산책횟수 계산
        recoWalkMinute = walkTimeCalculater.WalkMinuteCalculater(userID, saveFile); //추천 산책시간 계산

        //프로그레스바에 이용할 것임
        recoWalkMinuteForPro = recoWalkMinute * 60; // 분 -> 초 기준으로 바꿈


        //산책시간 조정하기
        tv_recowalktime.setText("하루 " + recoWalkTime + "회/ " + recoWalkMinute + "분");

    }

    //산책한 산책시간 흘러가게 하기 //프로그레스바 영향 0
    void playWalkTime(){

        if(isRunning == true){
            timeThread.start();
            imgbtn_play.setVisibility(View.GONE);
            imgbtn_stop.setVisibility(View.VISIBLE);
        }

    }

    //산책한 산책시간 일시정지 시키기 //프로그레스바 영향 x
    void stopWalkTime(){

        isRunning = (!isRunning);


    }

    //tv_walktime 에 산책시간 반영하기
    void set_tv_walktime(String result){
        //텍스트뷰
        TextView tv_walktime = (TextView) findViewById(R.id.tv_walktime);
        tv_walktime.setText(result );

    }



    //산책한 시간 파일에 저장
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    void saveWalkTime(Integer currntWalkMinute){
        Integer oldWalkTime = 0 ;
        Integer oldWalkMinute = 0 ;
        Integer newWalkTime = 0;
        Integer newWalkMinute = 0 ;

        //날짜
        Date today;
        SimpleDateFormat format1;
        format1 = new SimpleDateFormat("yyyy년 MM월 dd일");
        today = new Date();

        //파일 이름
        final String fileName = "/walkTimeMinute"+userID+".txt";

        // 먼저 파일을 읽고 오늘 날짜 정보가 파일에 써있지 않으면 이어쓰기 (파일의 마지막 날짜 != 시스템 날짜)
        // 파일을 읽고 오늘 날짜 정보가 파일에 써있으면 그 내용에 시간을 합산하여 이어쓰기 (시간과 횟수 모두 더함)

        //시간 스탑
        timeThread.interrupt();

        //파일 읽기
        ArrayList<WalkTimeMinuteResult> walkTimeMinuteResultsList = new ArrayList<WalkTimeMinuteResult>();
        //권한 필요 menifests 파일에 읽기 쓰기 권한 추가 필요
        //파일생성
        File saveFile = new File(getFilesDir() , "/userData"); // 저장 경로
        //폴더생성
        if(!saveFile.exists()){ // 폴더 없을 경우
            saveFile.mkdir(); // 폴더 생성
        }


        try(
                FileReader rw = new FileReader(saveFile+fileName);
                BufferedReader br = new BufferedReader( rw );
        ){


            String readLine = null ;
            Integer i = 1;


            while( ( readLine = br.readLine() ) != null ){ //파일 읽기
                //readLine 저장해야함
                // 1: 날짜 2: 1번 산책 횟수 3:  산책 시간

                WalkTimeMinuteResult buffer = new WalkTimeMinuteResult();

                buffer.setToday(readLine);
                buffer.setWalkTime(Integer.parseInt(br.readLine()));
                buffer.setWalkMinute(Integer.parseInt(br.readLine()));

                walkTimeMinuteResultsList.add(buffer);

                Log.d("123", "\n"+"\n"+ walkTimeMinuteResultsList.get(i-1).getToday() +"\n"+ walkTimeMinuteResultsList.get(i-1).getWalkTime() +"\n"+ walkTimeMinuteResultsList.get(i-1).getWalkMinute() +"\n"+"모든 요소"  );
                i++;


            }
            //끝
            Log.d("123", "\n"+walkTimeMinuteResultsList.size() + "워크타임미닛리스트  사이즈\n"  +walkTimeMinuteResultsList.get(walkTimeMinuteResultsList.size() -1).getToday() +"\n"+ walkTimeMinuteResultsList.get(walkTimeMinuteResultsList.size() -1).getWalkTime() +"\n"+ walkTimeMinuteResultsList.get(walkTimeMinuteResultsList.size() -1).getWalkMinute() +"\n"+"마지막 요소"  );

        }catch ( IOException e ) {
            System.out.println(e);
        }

        //먼저 읽어왔는데, 전체에서 이전에 쓴 내용이 없을 때 =산책 처음한 경우 => 이어쓰기
        if( walkTimeMinuteResultsList.size() == 0 ){

            //파일 이어쓰기
            try(
                    // 파일 객체 생성
                    FileWriter fw_append = new FileWriter(saveFile+fileName, true);
            ){

                StringBuffer str = new StringBuffer();
                str.append(format1.format(today).toString()+"\n");
                str.append(1+"\n"); //현재 산책횟수 1번  / 값 집어넣기
                str.append(currntWalkMinute+"\n");


                //파일 쓰기
                fw_append.write(String.valueOf(str));

//                Log.d("77777777777777777777", String.valueOf(str)+"전에 쓴 내용이 없을 때 이어쓰기 성공 **********************");
//                Toast.makeText(MyStartWalk.this, "내용이없어서 성공적으로 이어썼습니다.", Toast.LENGTH_SHORT).show();

            } catch (IOException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
        }

        else{ //읽어봤더니 전에 쓴 내용이 있을 때

            //리스트의 가장 마지막 산책 정보 (횟수, 시간)
            oldWalkTime = walkTimeMinuteResultsList.get(walkTimeMinuteResultsList.size() - 1).getWalkTime();
            oldWalkMinute = walkTimeMinuteResultsList.get(walkTimeMinuteResultsList.size() - 1).getWalkMinute();


//            Integer sec, min, hour;
//            int sec = (msg.arg1 / 100) % 60;
//            int min = (msg.arg1 / 100) / 60;
//            int hour = (msg.arg1 / 100) / 360;

            //횟수를 더해서 값 저장
            newWalkTime = oldWalkTime + 1 ;
            newWalkMinute = oldWalkMinute + currntWalkMinute ;

            //읽어봤더니 전에 쓴 내용이 있을 때
            //(기존내용) 복사해서 / 파일에 쓸지 이어서 쓸지 결정

            //파일의 마지막 날짜와 오늘 날짜가 같다면 = 오늘 날짜 산책시간에 시간을 합산함  = 덮어쓰기
            if(format1.format(today).toString().equals(walkTimeMinuteResultsList.get(walkTimeMinuteResultsList.size() -1 ).getToday())){

                try(
                        FileWriter fw = new FileWriter(saveFile+fileName, false);
                ){


                    //리스트 속 모든 정보 (마지막꺼 뺀) + 지금 들어가는 정보 저장 (덮어쓰기)
                    // 1날짜 2 산책 횟수 3 산책한 시간
                    StringBuffer str = new StringBuffer();
                    for(int i=0 ; i < walkTimeMinuteResultsList.size()-2 ; i++){
                        str.append(walkTimeMinuteResultsList.get(i).getToday()+"\n");
                        str.append(walkTimeMinuteResultsList.get(i).getWalkTime()+"\n");
                        str.append(walkTimeMinuteResultsList.get(i).getWalkMinute()+"\n");
//
                    }
                    //지금 덮어쓰려는 정보 덮어쓰기 (값 더하기)

                    str.append(format1.format(today).toString()+"\n"); //현재 날짜
                    str.append(newWalkTime+"\n");
                    str.append(newWalkMinute+"\n");

                    //파일 쓰기
                    fw.write(String.valueOf(str));
                    Log.d("77777777777777777777", String.valueOf(str)+"날짜가 같을 때 덮어쓰기 저장 성공**********************");


                } catch (IOException e1) {
                    // TODO Auto-generated catch bloc
                    e1.printStackTrace();
                }



            }

            else{ //날짜가 다르다면

                //파일 이어쓰기
                try(
                        FileWriter fw_append = new FileWriter(saveFile+fileName, true);
                ){

                    StringBuffer str = new StringBuffer();
                    str.append(format1.format(today).toString()+"\n");
                    str.append(newWalkTime+"\n");
                    str.append(newWalkMinute+"\n");

                    //파일 쓰기
                    fw_append.write(String.valueOf(str));

//                    Log.d("77777777777777777777", String.valueOf(str)+"위 정보를 날짜가 다를 때 이어쓰기 저장 성공**********************");
//                    Toast.makeText(MyStartWalk.this, "성공적으로 날짜가 다를때 이어쓰기 저장 성공.", Toast.LENGTH_SHORT).show();

                } catch (IOException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }
            }
        }

    }
    //파일에 기록하기 (끝)

    //

    public class TimeThread implements Runnable{
        @Override
        public void run() {
            int i = 0;

            while (true) {
                while (isRunning) { //일시정지를 누르면 멈춤
                    Message msg = new Message();
                    msg.arg1 = i++;
                    handler.sendMessage(msg);

                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                set_tv_walktime("");
                                set_tv_walktime("00 시간 00 분 00 초 00");

                            }
                        });
                        return; // 인터럽트 받을 경우 return
                    }
                }
            }

        }
    }


}//클래스 괄호


