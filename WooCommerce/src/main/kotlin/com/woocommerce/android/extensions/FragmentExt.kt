package com.woocommerce.android.extensions

import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController

/**
 * A helper function that sets the submitted key-value pair in the Fragment's SavedStateHandle. The value can be
 * observed as a LiveData using the same key - see the Fragment.handleResult() extension function. This mechanism is
 * used to facilitate the request-result communication between 2 separate fragments.
 *
 * @param [key] A unique string that is the same as the one used in [handleResult]
 * @param [result] A result value to be returned
 * @param [destinationId] an optional destinationId, that can be used to navigate up to a specified destination
 *
 */
fun <T> Fragment.navigateBackWithResult(key: String, result: T, @IdRes destinationId: Int? = null) {
    val entry = if (destinationId != null) {
        findNavController().getBackStackEntry(destinationId)
    } else {
        findNavController().previousBackStackEntry
    }
    entry?.savedStateHandle?.set(key, result)

    if (destinationId != null) {
        findNavController().popBackStack(destinationId, false)
    } else {
        findNavController().navigateUp()
    }
}

/**
 * A helper function that returns a notification in the Fragment's SavedStateHandle. Its purpose is to notify the caller
 * without returning any value. The notice can be observed as a LiveData using the same key - see the
 * Fragment.handleNotice() extension function.
 *
 * @param [key] A unique string that is the same as the one used in [handleNotice]
 * @param [destinationId] an optional destinationId, that can be used to navigating up to a specified destination
 */
fun Fragment.navigateBackWithNotice(key: String, @IdRes destinationId: Int? = null) {
    navigateBackWithResult(key, key, destinationId)
}

/**
 * A helper function that subscribes a supplied handler function to the Fragment's SavedStateHandle LiveData associated
 * with the supplied key.
 *
 * @param [key] A unique string that is the same as the one used in [navigateBackWithResult]
 * @param [entryId] An optional ID to identify the correct back stack entry. It's required when calling [handleResult]
 *  from TopLevelFragment or Dialog (otherwise the result will get lost upon configuration change)
 * @param [handler] A result handler
 *
 * Note: The handler is called only if the value wasn't handled before (i.e. the data is fresh). Once the observer is
 * called, the boolean value is updated. This puts a limit on the number of observers for a particular key-result pair
 * to 1.
 */
fun <T> Fragment.handleResult(key: String, entryId: Int? = null, handler: (T) -> Unit) {
    val entry = if (entryId != null) {
        findNavController().getBackStackEntry(entryId)
    } else {
        findNavController().currentBackStackEntry
    }

    entry?.savedStateHandle?.let { saveState ->
        saveState.getLiveData<T?>(key).observe(
            this.viewLifecycleOwner,
            Observer {
                it?.let {
                    handler(it)
                    saveState.set(key, null)
                }
            }
        )
    }
}

/**
 * A helper function that subscribes a supplied handler function to the dialog Fragment's SavedStateHandle LiveData
 * associated with the supplied key. This method *must* be used for handling results from dialogs because the entry ID
 * is required.
 *
 * @param [key] A unique string that is the same as the one used in [navigateBackWithResult]
 * @param [entryId] A mandatory ID to identify the correct back stack entry. It's required when calling [handleResult]
 *  from TopLevelFragment or Dialog (otherwise the result will get lost upon configuration change)
 * @param [handler] A result handler
 *
 * Note: The handler is called only if the value wasn't handled before (i.e. the data is fresh). Once the observer is
 * called, the value is nulled and the handler won't be called. This puts a limit on the number of observers for
 * a particular key-result pair to 1.
 */
fun <T> Fragment.handleDialogResult(key: String, entryId: Int, handler: (T) -> Unit) {
    handleResult(key, entryId, handler)
}

/**
 * A helper function that subscribes a supplied notice handler function to the dialog Fragment's SavedStateHandle
 * LiveData associated with the supplied key. Its purpose is to handle a notice without any value.
 * This method *must* be used for handling notices from dialogs because the entry ID is required.
 *
 * @param [key] A unique string that is the same as the one used in [navigateBackWithNotice]
 * @param [entryId] A mandatory ID to identify the correct back stack entry. It's required when calling [handleNotice]
 *  from TopLevelFragment or Dialog (otherwise the result will get lost upon configuration change)
 * @param [handler] A result handler
 *
 * Note: The handler is called only if the value wasn't handled before (i.e. the data is fresh). Once the observer is
 * called, the value is nulled and the handler won't be called. This puts a limit on the number of observers for
 * a particular key-result pair to 1.
 */
fun <T> Fragment.handleDialogNotice(key: String, entryId: Int, handler: (T) -> Unit) {
    handleResult(key, entryId, handler)
}

/**
 * A helper function that subscribes a supplied handler function to the Fragment's SavedStateHandle LiveData associated
 * with the supplied key. Its purpose is to handle a notice without any value.
 *
 * @param [key] A unique string that is the same as the one used in [navigateBackWithNotice]
 * @param [entryId] A mandatory ID to identify the correct back stack entry. It's required when calling [handleNotice]
 *  from TopLevelFragment or Dialog (otherwise the result will get lost upon configuration change)
 * @param [handler] A result handler
 *
 * Note: The handler is called only if the value wasn't handled before (i.e. the data is fresh). Once the observer is
 * called, the value is nulled and the handler won't be called. This puts a limit on the number of observers for
 * a particular key-result pair to 1.
 */
fun Fragment.handleNotice(key: String, entryId: Int? = null, handler: () -> Unit) {
    val entry = if (entryId != null) {
        findNavController().getBackStackEntry(entryId)
    } else {
        findNavController().currentBackStackEntry
    }

    entry?.savedStateHandle?.let { saveState ->
        saveState.getLiveData<String>(key).observe(
            this.viewLifecycleOwner,
            Observer {
                it?.let {
                    handler()
                    saveState.set(key, null)
                }
            }
        )
    }
}
