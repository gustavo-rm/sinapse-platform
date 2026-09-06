/**
 * The failures the coordinator raises deliberately.
 *
 * <p>Each carries an entry of the shared catalogue and no message of its own. That matters more
 * here than elsewhere: an exception message from the middle of an erasure would quote the data
 * being erased.
 */
package br.com.sinapse.platform.datarights.internal.error;
