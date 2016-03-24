package bluetooth.app.max.com.xmgbluetoothchat;

import java.io.File;
import java.util.Date;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.ImageView;
import android.widget.Toast;

public class PickImageUtil {

	private static final String CacheFolder = "cache";
	private static final int PHOTO_REQUEST_CAREMA = 1;// 拍照
	private static final int PHOTO_REQUEST_GALLERY = 2;// 从相册中选择
	private static final int PHOTO_REQUEST_CUT = 3;// 结果

	private File tempFile;
	private static ImageView iv_image;
	private static PickImageUtil mPickImageUtil;
	static Activity fromActivity;

	public static synchronized PickImageUtil getInstance(Activity activity) {
		fromActivity = activity;
		if (mPickImageUtil == null) {
			mPickImageUtil = new PickImageUtil();
		}
		return mPickImageUtil;
	}

	public void showPickImageDialog() {
		showPickImageDialog(null);
	}
	public void showPickImageDialog(ImageView imageview) {
		iv_image = imageview;
		new AlertDialog.Builder(fromActivity)
				.setTitle("请选择获取图片方式")
				.setNegativeButton("拍  照",
						new DialogInterface.OnClickListener() {

							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								camera();
							}
						})
				.setPositiveButton("图  库",
						new DialogInterface.OnClickListener() {

							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								gallery();
							}
						}).show();
	}

	/*
	 * 从相册获取
	 */
	public void gallery() {
		// 激活系统图库，选择一张图片
		Intent intent = new Intent(Intent.ACTION_PICK);
		intent.setType("image/*");
		// 开启一个带有返回值的Activity，请求码为PHOTO_REQUEST_GALLERY
		fromActivity.startActivityForResult(intent, PHOTO_REQUEST_GALLERY);
	}

	/*
	 * 从相机获取
	 */
	public void camera() {
		// 激活相机
		Intent intent = new Intent("android.media.action.IMAGE_CAPTURE");
		// 判断存储卡是否可以用，可用进行存储
		if (hasSdcard()) {
			tempFile = new File(Environment.getExternalStorageDirectory()
					.getAbsolutePath() + "/" + CacheFolder + "/",
					new Date().getTime() + ".jpg");
			// 从文件中创建uri
			Uri uri = Uri.fromFile(tempFile);
			intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
		}
		// 开启一个带有返回值的Activity，请求码为PHOTO_REQUEST_CAREMA
		fromActivity.startActivityForResult(intent, PHOTO_REQUEST_CAREMA);
	}

	/*
	 * 剪切图片
	 */
	private void crop(Uri uri) {
		// 裁剪图片意图
		Intent intent = new Intent("com.android.camera.action.CROP");
		intent.setDataAndType(uri, "image/*");
		intent.putExtra("crop", "true");
		// 裁剪框的比例，1：1
		intent.putExtra("aspectX", 1);
		intent.putExtra("aspectY", 1);
		// 裁剪后输出图片的尺寸大小
		intent.putExtra("outputX", 350);
		intent.putExtra("outputY", 350);

		intent.putExtra("scale", true);// 去黑边
		intent.putExtra("scaleUpIfNeeded", true);// 去黑边
		intent.putExtra("outputFormat", "JPEG");// 图片格式
		intent.putExtra("noFaceDetection", true);// 取消人脸识别
		intent.putExtra("return-data", true);
		// 开启一个带有返回值的Activity，请求码为PHOTO_REQUEST_CUT
		fromActivity.startActivityForResult(intent, PHOTO_REQUEST_CUT);
	}

	/*
	 * 判断sdcard是否被挂载
	 */
	private boolean hasSdcard() {
		if (Environment.getExternalStorageState().equals(
				Environment.MEDIA_MOUNTED)) {
			return true;
		} else {
			return false;
		}
	}

	// 要用Activity的 onActivityResult 调用这个方法。
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == PHOTO_REQUEST_GALLERY) {
			// 从相册返回的数据
			if (data != null) {
				// 得到图片的全路径
				Uri uri = data.getData();
				crop(uri);
			}

		} else if (requestCode == PHOTO_REQUEST_CAREMA) {
			// 从相机返回的数据
			if (hasSdcard()) {
				crop(Uri.fromFile(tempFile));
			} else {
				Toast.makeText(fromActivity, "未找到存储卡，无法存储照片！", Toast.LENGTH_SHORT).show();
			}

		} else if (requestCode == PHOTO_REQUEST_CUT) {
			// 从剪切图片返回的数据
			if (data != null) {
				Bitmap bitmap = data.getParcelableExtra("data");
				if(iv_image == null){
					doFinish(bitmap, 0);
				}else {
					iv_image.setImageBitmap(bitmap);
					// TODO 写一个接口，调用传给BaseInfoActivity一个bitmap。然后存储到sdcard中。
					doFinish(bitmap, iv_image.getId());
				}
			}
			try {
				// 将临时文件删除
				tempFile.delete();
			} catch (Exception e) {
				e.printStackTrace();
			}

		}
	}

	public Uri takePhotoResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == PHOTO_REQUEST_GALLERY) {
			// 从相册返回的数据
			if (data != null) {
				// 得到图片的全路径
				String[] filePathColumn = { MediaStore.Images.Media.DATA };
				// 获取选中图片的路径
				String photoPath = "";
				if (fromActivity == null)
					return null;
				Cursor cursor = fromActivity.getContentResolver().query(
						data.getData(), filePathColumn, null, null, null);
				if (cursor.moveToFirst()) {
					photoPath = cursor.getString(cursor
							.getColumnIndex(filePathColumn[0]));
				}
				cursor.close();
				Uri uri = Uri.parse(photoPath);
				return uri;
			}

		} else if (requestCode == PHOTO_REQUEST_CAREMA) {
			// 从相机返回的数据
			if (hasSdcard()) {
				// TODO
				// crop(Uri.fromFile(tempFile));
				Uri uri = Uri.fromFile(tempFile);
				return uri;
			} else {
				Toast.makeText(fromActivity, "未找到存储卡，无法存储照片！", Toast.LENGTH_SHORT).show();
				return null;
			}

		}
		return null;
	}

	public interface IGetSelectBitmap {
		public void onFinish(Bitmap bitmap, int id);
	}

	IGetSelectBitmap callback;

	public void doFinish(Bitmap bitmap, int id) {
		callback.onFinish(bitmap, id);
	}

	public void setCallback(IGetSelectBitmap callback) {
		this.callback = callback;
	}
}
