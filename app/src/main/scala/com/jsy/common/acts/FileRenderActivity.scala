/**
 * Secret
 * Copyright (C) 2021 Secret
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.jsy.common.acts

import java.io.{BufferedReader, InputStream, InputStreamReader}

import android.content.{Context, Intent}
import android.graphics.Bitmap
import android.graphics.drawable.PictureDrawable
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.{Bundle, ParcelFileDescriptor}
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{ImageView, TextView}
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.viewpager.widget.{PagerAdapter, ViewPager}
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.github.chrisbanes.photoview.PhotoView
import com.jsy.common.acts.FileRenderActivity._
import com.jsy.common.svg.SvgSoftwareLayerSetter
import com.waz.threading.Threading
import com.waz.zclient.{BaseActivity, R}

import scala.concurrent.Future
import scala.util.Try

class FileRenderActivity extends BaseActivity {

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_file_render)

    var fileName: String = null

    setToolbarNavigtion(findById[Toolbar](R.id.toolbar), this)
    Option(findById[TextView](R.id.title_textView)).foreach { view =>
      Option(getIntent).flatMap(it => Option(it.getStringExtra(PARAMS_FILE_NAME)))
        .foreach { it => fileName = it; view.setText(it) }
    }

    Option(fileName).filter(_.lastIndexOf(".") > -1)
      .map(it => it.substring(it.lastIndexOf(".") + 1))
      .map(_.toLowerCase())
      .foreach {
        case "svg" =>
          val fragment = new SvgRenderFragment
          fragment.setArguments(getIntent.getExtras)
          getSupportFragmentManager.beginTransaction().replace(R.id.content_layout, fragment).commit()
        case "pdf" =>
          val fragment = new PdfRenderFragment
          fragment.setArguments(getIntent.getExtras)
          getSupportFragmentManager.beginTransaction().replace(R.id.content_layout, fragment).commit()
        case "txt" =>
          val fragment = new TxtRenderFragment
          fragment.setArguments(getIntent.getExtras)
          getSupportFragmentManager.beginTransaction().replace(R.id.content_layout, fragment).commit()
        case _ =>
      }
  }
}

object FileRenderActivity {

  private val PARAMS_FILE_PATH = "file_path"
  private val PARAMS_FILE_NAME = "file_name"

  def start(context: Context, fileName: String, filePath: String): Unit = {
    context.startActivity(new Intent(context, classOf[FileRenderActivity])
      .putExtra(PARAMS_FILE_NAME, fileName)
      .putExtra(PARAMS_FILE_PATH, filePath))
  }

  private class PdfRenderFragment extends Fragment {

    private var mParcelFileDescriptor: ParcelFileDescriptor = _
    private var mPdfRenderer: PdfRenderer = _

    private var mPdfViewPager: ViewPager = _

    override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
      inflater.inflate(R.layout.fragment_pdf_render, container, false)
    }

    override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
      super.onViewCreated(view, savedInstanceState)
      mPdfViewPager = view.findViewById[ViewPager](R.id.pdf_viewpPager)
    }

    override def onActivityCreated(savedInstanceState: Bundle): Unit = {
      super.onActivityCreated(savedInstanceState)
      Option(getArguments).flatMap(it => Option(it.getString(PARAMS_FILE_PATH))).foreach(openRender)
    }

    override def onDestroy(): Unit = {
      super.onDestroy()
      closeRender()
    }

    private def openRender(filePath: String): Unit = {
      mParcelFileDescriptor = getContext.getContentResolver.openFileDescriptor(Uri.parse(filePath), "r")
      if (mParcelFileDescriptor != null) {
        mPdfRenderer = new PdfRenderer(mParcelFileDescriptor)
      }

      mPdfViewPager.setAdapter(new PdfAdapter)
    }

    private def closeRender(): Unit = {
      Try(mPdfRenderer.close())
      Try(mParcelFileDescriptor.close())
    }

    private class PdfAdapter extends PagerAdapter {
      override def getCount: Int = {
        Option(mPdfRenderer).map(_.getPageCount).getOrElse(0)
      }

      override def isViewFromObject(view: View, `object`: Any): Boolean = {
        view == `object`
      }

      override def instantiateItem(container: ViewGroup, position: Int): AnyRef = {
        val pdfPhotoView = new PhotoView(getContext)
        pdfPhotoView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        pdfPhotoView.setEnabled(true)
        if (getCount <= position) {
          return pdfPhotoView
        }

        val currentPage = mPdfRenderer.openPage(position)
        val bitmap = Bitmap.createBitmap(1080, 1760, Bitmap.Config.ARGB_8888)
        currentPage.render(bitmap, null, null, 1)
        pdfPhotoView.setImageBitmap(bitmap)
        currentPage.close()
        container.addView(pdfPhotoView)
        pdfPhotoView
      }

      override def destroyItem(container: ViewGroup, position: Int, `object`: Any): Unit = {
        Try(`object`.asInstanceOf[View]).foreach(container.removeView)
      }
    }

  }

  private class TxtRenderFragment extends Fragment {

    private var contentTextView: TextView = _

    override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
      inflater.inflate(R.layout.fragment_txt_render, container, false)
    }

    override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
      super.onViewCreated(view, savedInstanceState)
      contentTextView = view.findViewById[TextView](R.id.content_textView)
    }

    override def onActivityCreated(savedInstanceState: Bundle): Unit = {
      super.onActivityCreated(savedInstanceState)
      Option(getArguments).flatMap(it => Option(it.getString(PARAMS_FILE_PATH))).foreach(it => Future(readFile(it))(Threading.IOThreadPool))
    }

    private def readFile(filePath: String): Unit = {

      var fileInputStream: InputStream = null
      var inputStreamReader: InputStreamReader = null
      var bufferedReader: BufferedReader = null
      try {
        fileInputStream = getContext.getContentResolver.openInputStream(Uri.parse(filePath))
        inputStreamReader = new InputStreamReader(fileInputStream, "GB2312")
        bufferedReader = new BufferedReader(inputStreamReader)
        var lineStr: String = null
        while ( {
          lineStr = bufferedReader.readLine()
          lineStr
        } != null) {
          val lineBytes = lineStr.getBytes("UTF-8")
          Future {
            contentTextView.append(new String(lineBytes, 0, lineBytes.length, "UTF-8"))
            contentTextView.append("\n")
          }(Threading.Ui)
        }
      } catch {
        case e: Throwable =>
          e.printStackTrace()
      } finally {
        if (fileInputStream != null) {
          Try(fileInputStream.close())
        }
        if (inputStreamReader != null) {
          Try(inputStreamReader.close())
        }
        if (bufferedReader != null) {
          Try(bufferedReader.close())
        }
      }
    }
  }

  private class SvgRenderFragment extends Fragment {

    private var contentImageView: ImageView = _

    override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
      inflater.inflate(R.layout.fragment_svg_render, container, false)
    }

    override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
      super.onViewCreated(view, savedInstanceState)
      contentImageView = view.findViewById[ImageView](R.id.content_imageView)
    }

    override def onActivityCreated(savedInstanceState: Bundle): Unit = {
      super.onActivityCreated(savedInstanceState)
      Option(getArguments).flatMap(it => Option(it.getString(PARAMS_FILE_PATH))).foreach(readSvg)
    }

    private def readSvg(filePath: String): Unit = {
      Glide.`with`(this).as(classOf[PictureDrawable])
        .listener(new SvgSoftwareLayerSetter)
        .error(R.drawable.ic_launcher_wire_candidate)
        .transition(withCrossFade())
        .load(Uri.parse(filePath)).into(contentImageView)
    }
  }

}
