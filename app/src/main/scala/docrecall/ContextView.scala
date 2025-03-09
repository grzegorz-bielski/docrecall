package docrecall

import cats.effect.*
import cats.syntax.all.*
import org.http4s.{scalatags as _, h2 as _, *}
import scalatags.Text.all.*
import scalatags.Text.tags2.{progress, details, summary}

import docrecall.chat.*
import docrecall.rag.*
import docrecall.common.*
import docrecall.viewpartials.*

object ContextView extends HtmxView:
  private val uploadedFilesListId = "uploaded-files-list"
  private val uploadModalId       = "uploadModal"

  def view(
    contexts: Vector[ContextInfo],
    contextUrl: ContextId => String,
    contextInfo: ContextInfo,
    chatPostUrl: String,
    contextUpdateUrl: String,
    uploadUrl: String,
    documents: Vector[Document.Info],
    fileFieldName: String,
    documentDeleteUrl: DocumentDeleteUrl,
  )(using AppConfig) = RootLayoutView.view(
    contexts = contexts,
    activeContextId = contextInfo.id,
    contextUrl = contextUrl,
    children = div(
      cls := "grid grid-cols-1 md:grid-cols-5",
      div(
        cls := "md:col-span-3 md:px-5",
        configMenu(
          contextUrl = contextUrl,
          uploadUrl = uploadUrl,
          contextUpdateUrl = contextUpdateUrl,
          documents = documents,
          fileFieldName = fileFieldName,
          contextInfo = contextInfo,
          documentDeleteUrl = documentDeleteUrl,
        ),
      ),
      ChatView.view(chatPostUrl = chatPostUrl),
    ),
  )

  def uploadedDocuments(docs: Vector[Document.Ingested], documentDeleteUrl: DocumentDeleteUrl) =
    ul(
      `hx-swap-oob` := s"beforeend:#$uploadedFilesListId",
      docs.map(ingested => documentItem(documentDeleteUrl)(ingested.info)),
    )

  private def configMenu(
    contextUrl: ContextId => String,
    uploadUrl: String,
    contextUpdateUrl: String,
    documents: Vector[Document.Info],
    fileFieldName: String,
    contextInfo: ContextInfo,
    documentDeleteUrl: DocumentDeleteUrl,
  ) =
    div(
      cls := "tabs",
      tab(
        "Knowledge Base",
        knowledgeBase(
          uploadUrl = uploadUrl,
          documents = documents,
          fileFieldName = fileFieldName,
          documentDeleteUrl = documentDeleteUrl,
        ),
        checked = true,
      ),
      tab("Context Settings", contextSettings(contextInfo = contextInfo, contextUpdateUrl = contextUpdateUrl)),
      tab("Advanced", advancedSettings(contextInfo = contextInfo, contextUrl = contextUrl)),
    )

  private def tab(name: String, content: Modifier, checked: Boolean = false) =
    Seq(
      input(
        `type`             := "radio",
        attr("name")       := "my_tabs_1",
        role               := "tab",
        cls                :=
          Vector(
            "tab",
            "rounded-box",
          )
            .mkString(" "),
        attr("aria-label") := name,
        Option.when(checked)(attr("checked") := "checked"),
      ),
      div(
        role               := "tabpanel",
        cls                := "tab-content bg-base-100 p-2 md:pt-6 md:h-[calc(100dvh-14rem)]! overflow-y-scroll",
        content,
      ),
    )

  private def advancedSettings(
    contextInfo: ContextInfo,
    contextUrl: ContextId => String,
  ) =
    // TODO: add dialog for deletion confirmation
    // TODO: broken...
    div(
      button(
        cls         := "btn btn-sm btn-error btn-disabled",
        `hx-delete` := contextUrl(contextInfo.id),
        "Delete context",
      ),
    )

  private def contextSettings(
    contextInfo: ContextInfo,
    contextUpdateUrl: String,
  ) =
    val promptTemplateJson =
      contextInfo.promptTemplate.asJson(indentStep = 2).combineAll

    val retrievalSettingsJson =
      contextInfo.retrievalSettings.asJson(indentStep = 2).combineAll

    val chatCompletionSettingsJson =
      contextInfo.chatCompletionSettings.asJson(indentStep = 2).combineAll

    div(
      form(
        `hx-post` := contextUpdateUrl,
        `hx-swap` := "none",
        div(
          cls := "grid grid-cols-1 md:grid-cols-2 gap-2",
          formInput(
            labelValue = "Name",
            fieldName = "name",
            value = contextInfo.name,
          ),
          formInput(
            labelValue = "Description",
            fieldName = "description",
            value = contextInfo.description,
          ),
        ),
        formTextarea(
          labelValue = "Prompt Template",
          fieldName = "promptTemplate",
          value = promptTemplateJson,
        ),
        formTextarea(
          labelValue = "Retrieval Settings",
          fieldName = "retrievalSettings",
          value = retrievalSettingsJson,
        ),
        formTextarea(
          labelValue = "Chat Completion Settings",
          fieldName = "chatCompletionSettings",
          value = chatCompletionSettingsJson,
        ),
        div(
          cls := "grid grid-cols-1 md:grid-cols-2 gap-2",
          formSelect(
            labelValue = "Chat Model",
            fieldName = "chatModel",
            options = modelOptions(contextInfo.chatModel),
          ),
          formSelect(
            labelValue = "Embeddings Model",
            fieldName = "embeddingsModel",
            options = modelOptions(contextInfo.embeddingsModel),
          ),
        ),
        button(
          cls := "btn btn-secondary block ml-auto mt-2",
          "Save",
        ),
      ),
    )

  private def formTextarea(labelValue: String, fieldName: String, value: String) =
    formControl(
      labelValue,
      textarea(
        cls  := "textarea w-full h-64",
        name := fieldName,
        value,
      ),
    )

  private def formInput(labelValue: String, fieldName: String, value: String) =
    formControl(
      labelValue,
      input(
        cls           := "input w-full",
        name          := fieldName,
        attr("value") := value,
      ),
    )

  final case class SelectOption(label: String, value: String, selected: Boolean = false)

  private def formSelect(labelValue: String, fieldName: String, options: Vector[SelectOption]) =
    formControl(
      labelValue,
      select(
        cls  := "select w-full",
        name := fieldName,
        options.map: op =>
          option(
            value := op.value,
            Option.when(op.selected)(selected := true),
            op.label,
          ),
      ),
    )

  private def modelOptions(current: Model) = Model.values.toVector.map: model =>
    SelectOption(
      label = model.name,
      value = model.name,
      selected = model == current,
    )

  private def formControl(labelValue: String, input: Modifier) =
    fieldset(
      cls := "fieldset w-full rounded-box bg-base-200 p-4 rounded-box",
      legend(
        cls := "fieldset-legend",
        labelValue,
      ),
      input,
    )

  type DocumentDeleteUrl = Document.Info => String

  private def documentItem(documentDeleteUrl: DocumentDeleteUrl)(document: Document.Info) =
    val documentFileId = s"file-${document.id}"

    li(
      id  := documentFileId,
      cls := "group rounded-r-box hover:bg-base-300 focus-within:bg-base-300 outline-hidden mr-5",
      div(
        cls := "min-h-8 py-2 px-3 text-xs flex gap-3 items-center",
        span(IconsView.documentIcon()),
        span(cls      := "text-wrap break-all", s"${document.name}"),
        button(
          `hx-delete` := documentDeleteUrl(document),
          `hx-target` := s"#$documentFileId",
          `hx-swap`   := "outerHTML",
          cls         := "btn btn-xs btn-ghost btn-square opacity-0 group-hover:opacity-100 group-focus-within:opacity-100 mr-0 ml-auto transition-none",
          "✕",
        ),
      ),
    )

  private def emptyItem() =
    li(
      cls := "hidden last:block",
      div(
        cls := "px-4 py-8 flex justify-center",
        p("Nothing here yet."),
      ),
    )

  private def knowledgeBase(
    uploadUrl: String,
    fileFieldName: String,
    documents: Vector[Document.Info],
    documentDeleteUrl: DocumentDeleteUrl,
  ) =

    val files =
      div(
        ul(
          cls := "max-h-96 overflow-y-scroll",
          id  := uploadedFilesListId,
          emptyItem(),
          documents.map(documentItem(documentDeleteUrl)),
        ),
      )

    val uploadFilesModal = ModalView.view(
      modalId = uploadModalId,
      buttonTitle = "Upload",
      modalTitle = "Upload your files",
      modalContent = uploadForm(
        uploadUrl = uploadUrl,
        fileFieldName = fileFieldName,
      ),
      buttonExtraClasses = Vector("btn-secondary block ml-auto"),
    )

    div(
      ul(
        cls := "bg-base-200 rounded-lg w-full max-w-s mb-4",
        li(
          details(
            attr("open") := true,
            summary(
              cls := Vector(
                "p-4 cursor-pointer rounded-lg",
                "bg-base-200 hover:bg-base-300 active:bg-base-400 focus:bg-base-400 outline-hidden transition-colors",
              ).mkString(" "),
              "Files",
              // TODO: add folder icon to the right, right now it breaks the summary marker
              // folderIcon(),
            ),
            files,
            div(
              cls := "p-5",
              uploadFilesModal.button,
            ),
          ),
        ),
      ),
      uploadFilesModal.window,
    )

  // https://uploadcare.com/blog/how-to-make-a-drag-and-drop-file-uploader/
  // https://http4s.org/v1/docs/multipart.html
  private def uploadForm(uploadUrl: String, fileFieldName: String) =
    fileUploader(
      attr("upload-url")      := uploadUrl,
      attr("file-field-name") := fileFieldName,
      attr("modal-id")        := uploadModalId,
      attr("max-size")        := "1073741824", // 1GiB
      attr("allowed-types")   :=
        Vector(
          // General
          "application/pdf",
          "text/plain",
          "text/html",
          "text/csv",
          "text/xml",
          "application/rtf",
          "application/json",
          "application/xml",
          "application/xhtml+xml",
          // MS
          "application/vnd.ms-excel",
          "application/msword",
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
          "application/vnd.openxmlformats-officedocument.presentationml.presentation",
          // OpenOffice
          "application/vnd.ms-powerpoint",
          "application/x-vnd.oasis.opendocument.spreadsheet",
          "application/vnd.oasis.opendocument.spreadsheet",
          "application/vnd.oasis.opendocument.presentation",
          "application/vnd.oasis.opendocument.text",
          // Other
          "application/x-abiword",
          // Ebooks
          "application/epub+zip",
          "application/x-mobipocket-ebook",
        ).mkString(","),
    )
