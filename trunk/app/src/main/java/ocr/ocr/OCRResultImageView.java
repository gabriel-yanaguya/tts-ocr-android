package ocr.ocr;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * Created by Chan on 08/03/2017.
 */

public class OCRResultImageView  extends ImageView{

    public OCRResultImageView(Context context) {
        super(context);
        // TODO Auto-generated constructor stub
    }

    public OCRResultImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onDraw(Canvas canvas) {

        Paint paint = new Paint();
        paint.setColor(Color.RED);
        canvas.drawRect(10, 50, 10, 50, paint);

        super.onDraw(canvas);
    }

}
