package fr.xebia.quiz.quiz;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.parse.FindCallback;
import com.parse.ParseException;
import com.parse.ParseQuery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import fr.xebia.quiz.R;
import fr.xebia.quiz.model.Question;
import fr.xebia.quiz.model.QuestionResult;
import fr.xebia.quiz.result.ResultFragment;
import timber.log.Timber;

import static fr.xebia.quiz.form.FormActivity.EXTRA_GUEST_ID;

public class QuestionFragment extends Fragment {

    private static final Random RANDOM = new Random();
    public static final Handler HANDLER = new Handler();

    public static final int QUIZ_QUESTION_COUNT = 10;
    public static final int TIMER = 1_000;
    public static final int DURATION = 10_000;
    public static final int NEXT_DELAY = 1_000;

    @Bind(R.id.questionText) TextView questionText;

    @Bind(R.id.answer0Text) Button answer0Button;
    @Bind(R.id.answer1Text) Button answer1Button;
    @Bind(R.id.answer2Text) Button answer2Button;

    @Bind(R.id.questionCountTextView) TextView questionCountTextView;
    @Bind(R.id.questionCountProgressBar) ProgressBar questionCountProgressBar;

    @Bind(R.id.timerTextView) TextView timerTextView;
    @Bind(R.id.timerProgressBar) ProgressBar timerProgressBar;

    private QuestionResult[] results;

    private Question[] quizQuestion;
    private int current = 0;
    private String guestId;
    private ValueAnimator animator;
    private long time;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle bundle = getArguments();
        if (bundle != null) {
            guestId = bundle.getString(EXTRA_GUEST_ID);
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_question, container, false);
        ButterKnife.bind(this, view);
        return view;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupQuestion();
    }

    private void setupQuestion() {
        ParseQuery<Question> query = new ParseQuery<>(Question.class);
        query.fromLocalDatastore();
        query.findInBackground(new FindCallback<Question>() {
            @Override
            public void done(List<Question> questions, ParseException e) {
                if (e == null) {
                    randomizeQuestion(questions);
                } else {
                    Timber.e(e, "Cannot get question");
                }
            }
        });
    }

    private void randomizeQuestion(List<Question> questions) {
        quizQuestion = new Question[QUIZ_QUESTION_COUNT];
        results = new QuestionResult[QUIZ_QUESTION_COUNT];
        Question[] questionArray = questions.toArray(new Question[questions.size()]);
        for (int i = 0; i < QUIZ_QUESTION_COUNT; i++) {
            int position = RANDOM.nextInt(questions.size() - 1 - i);
            Question question = questionArray[position];
            questionArray[position] = questionArray[questions.size() - 1 - i];
            quizQuestion[i] = question;
        }

        startQuiz();
    }

    private void startQuiz() {
        questionCountProgressBar.setMax(quizQuestion.length);

        animator = ValueAnimator.ofInt(TIMER, 0);
        animator.setDuration(DURATION);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int value = (int) animation.getAnimatedValue();
                timerProgressBar.setProgress(value);
                timerTextView.setText(String.format("%d'", value * TIMER / DURATION));
            }
        });
        animator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                // do nothing
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                onAnswerClick(null);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                // do nothing
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
                // do nothing
            }
        });

        next();
    }

    @OnClick({R.id.answer0Text, R.id.answer1Text, R.id.answer2Text})
    @SuppressWarnings("unused")
    public void onAnswerClick(Button answer) {
        answerButtonEnable(false);
        animator.pause();
        boolean correct = false;
        final Question question = quizQuestion[current];
        String text = "";
        if (answer != null) {
            text = (String) answer.getText();
            if (question.getCorrect().equals(text)) {
                correct = true;
            }
        }

        results[current] = new QuestionResult(question.getText(), text, question.getCorrect(), System.currentTimeMillis() - time);

        current++;

        if (correct) {
            correct();
        } else {
            wrong();
        }
    }

    private void answerButtonEnable(boolean enable) {
        answer0Button.setEnabled(enable);
        answer1Button.setEnabled(enable);
        answer2Button.setEnabled(enable);
    }

    private void wrong() {
        if (current < quizQuestion.length) {
            next();
        } else {
            result();
        }
    }

    private void correct() {
        if (current < quizQuestion.length) {
            next();
        } else {
            result();
        }
    }

    private void result() {
        getActivity()
                .getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.container, ResultFragment.newInstance(guestId, results), ResultFragment.class.getSimpleName())
                .commit();
    }

    private void next() {
        time = System.currentTimeMillis();
        HANDLER.postDelayed(new Runnable() {
            @Override
            public void run() {
                Question question = quizQuestion[current];

                questionText.setText(question.getText());

                List<String> answers = new ArrayList<>();
                answers.add(question.getCorrect());
                answers.add(question.getWrong1());
                answers.add(question.getWrong2());
                Collections.shuffle(answers);

                answer0Button.setText(answers.get(0));
                answer1Button.setText(answers.get(1));
                answer2Button.setText(answers.get(2));

                questionCountTextView.setText(String.format("%d/%d", current + 1, quizQuestion.length));
                questionCountProgressBar.setProgress(current + 1);

                answerButtonEnable(true);

                animator.start();
            }
        }, NEXT_DELAY);
    }

    public static Fragment newInstance(String guestId) {
        QuestionFragment fragment = new QuestionFragment();
        Bundle bundle = new Bundle();
        bundle.putString(EXTRA_GUEST_ID, guestId);
        fragment.setArguments(bundle);
        return fragment;
    }
}
