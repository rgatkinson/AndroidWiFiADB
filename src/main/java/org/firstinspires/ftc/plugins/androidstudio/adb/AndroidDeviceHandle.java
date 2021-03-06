package org.firstinspires.ftc.plugins.androidstudio.adb;

import com.android.ddmlib.IDevice;
import org.firstinspires.ftc.plugins.androidstudio.Configuration;
import org.firstinspires.ftc.plugins.androidstudio.adb.commands.GetSettingCommand;
import org.firstinspires.ftc.plugins.androidstudio.adb.commands.IfConfigCommand;
import org.firstinspires.ftc.plugins.androidstudio.util.AdbCommunicationException;
import org.firstinspires.ftc.plugins.androidstudio.util.EventLog;
import org.firstinspires.ftc.plugins.androidstudio.util.IpUtil;
import org.firstinspires.ftc.plugins.androidstudio.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * {@link AndroidDeviceHandle} represents a live connection to an {@link AndroidDevice}
 */
@SuppressWarnings("WeakerAccess")
public class AndroidDeviceHandle
    {
    //----------------------------------------------------------------------------------------------
    // State
    //----------------------------------------------------------------------------------------------

    public static final String TAG = "AndroidDeviceHandle";

    public static final Pattern patternIpAddress        = Pattern.compile("[0-9]{1,3}\\.*[0-9]{1,3}\\.*[0-9]{1,3}\\.*[0-9]{1,3}");
    public static final Pattern patternIpAddressAndPort = Pattern.compile("[0-9]{1,3}\\.*[0-9]{1,3}\\.*[0-9]{1,3}\\.*[0-9]{1,3}:[0-9]{1,5}");

    protected final IDevice device;
    protected final AndroidDevice androidDevice;

    //----------------------------------------------------------------------------------------------
    // Construction
    //----------------------------------------------------------------------------------------------

    public AndroidDeviceHandle(IDevice device, AndroidDevice androidDevice)
        {
        EventLog.dd(TAG, "open(id=%s at=%s)", androidDevice.getDebugDisplayName(), device.getSerialNumber());
        this.device = device;
        this.androidDevice = androidDevice;
        }

    public void close()
        {
        EventLog.dd(TAG, "close(id=%s at=%s)", androidDevice.getDebugDisplayName(), device.getSerialNumber());
        androidDevice.close(this);
        }

    public void debugDump(int indent, PrintStream out)
        {
        StringUtil.appendLine(indent, out, "handle=%s", getSerialNumber());
        }

    //----------------------------------------------------------------------------------------------
    // Accessing
    //----------------------------------------------------------------------------------------------

    public AndroidDevice getAndroidDevice()
        {
        return androidDevice;
        }

    public IDevice getDevice()
        {
        return device;
        }

    //----------------------------------------------------------------------------------------------
    // High level device accessing
    //----------------------------------------------------------------------------------------------

    /*
     * wlan0: Is the device on an infrastructure network?
     *
     * Example connected state:
     *  adb shell getprop | grep -y wlan0
     *      [dhcp.wlan0.dns1]: [75.75.75.75]
     *      [dhcp.wlan0.dns2]: [75.75.76.76]
     *      [dhcp.wlan0.dns3]: []
     *      [dhcp.wlan0.dns4]: []
     *      [dhcp.wlan0.domain]: []
     *      [dhcp.wlan0.gateway]: [192.168.0.1]
     *      [dhcp.wlan0.ipaddress]: [192.168.0.20]
     *      [dhcp.wlan0.leasetime]: [3600]
     *      [dhcp.wlan0.mask]: [255.255.255.0]
     *      [dhcp.wlan0.mtu]: []
     *      [dhcp.wlan0.pid]: [15503]
     *      [dhcp.wlan0.reason]: [BOUND]
     *      [dhcp.wlan0.result]: [ok]
     *      [dhcp.wlan0.server]: [192.168.0.1]
     *      [dhcp.wlan0.vendorInfo]: []
     *      [init.svc.dhcpcd_wlan0]: [running]
     *      [wifi.interface]: [wlan0]
     *
     * Example connected, then disconnected state:
     *      [dhcp.wlan0.dns1]: [75.75.75.75]
     *      [dhcp.wlan0.dns2]: [75.75.76.76]
     *      [dhcp.wlan0.dns3]: []
     *      [dhcp.wlan0.dns4]: []
     *      [dhcp.wlan0.domain]: []
     *      [dhcp.wlan0.gateway]: [192.168.0.1]
     *      [dhcp.wlan0.ipaddress]: [192.168.0.20]
     *      [dhcp.wlan0.leasetime]: [3600]
     *      [dhcp.wlan0.mask]: [255.255.255.0]
     *      [dhcp.wlan0.mtu]: []
     *      [dhcp.wlan0.pid]: [15503]
     *      [dhcp.wlan0.reason]: [BOUND]
     *      [dhcp.wlan0.result]: [failed]
     *      [dhcp.wlan0.server]: [192.168.0.1]
     *      [dhcp.wlan0.vendorInfo]: []
     *      [init.svc.dhcpcd_wlan0]: [stopped]
     *      [wifi.interface]: [wlan0]
     *
     * Example boot, non-connected state:
     *      [wifi.interface]: [wlan0]
     */

    /** Returns whether this device is currently connected to an infrastructure IP network */
    public boolean isWlanRunning()
        {
        return getStringProperty(Configuration.PROP_WLAN_STATUS, "stopped").equals("running");
        }

    /** If this device is currently or was recently connected to an infrastructure
     * IP network, then return the address used on same*/
    public @Nullable InetAddress getWlanAddress()
        {
        try {
            String value = getStringProperty(Configuration.PROP_WLAN_IP_ADDRESS, null);
            return value == null ? null : InetAddress.getByName(value);
            }
        catch (UnknownHostException e)
            {
            return null;
            }
        }

    @SuppressWarnings("ConstantConditions")
    public @NotNull String getUsbSerialNumber()
        {
        return getStringProperty(Configuration.PROP_USB_SERIAL_NUMBER);
        }

    public @NotNull String getSerialNumber()
        {
        return device.getSerialNumber();
        }

    public boolean isEmulator()
        {
        return device.isEmulator();
        }
    public boolean isTcpip()
        {
        return patternIpAddressAndPort.matcher(getSerialNumber()).matches();
        }
    public boolean isUSB()
        {
        return !isTcpip() && !isEmulator();
        }

    public @Nullable InetSocketAddress getInetSocketAddress()
        {
        if (isTcpip())
            {
            return IpUtil.parseInetSocketAddress(getSerialNumber());
            }
        return null;
        }

    public boolean isWifiDirectGroupOwner()
        {
        try {
            IfConfigCommand command = new IfConfigCommand(device, "p2p0");
            command.execute();
            return Configuration.WIFI_DIRECT_GROUP_OWNER_ADDRESS.equals(command.getInetAddress()) && command.isUp();
            }
        catch (AdbCommunicationException e)
            {
            EventLog.ee(TAG, "isWifiDirectGroupOwner() failed: %s; ignored: %s: %s", getUsbSerialNumber(), e.getMessage(), e.getCause().getMessage());
            return false;
            }
        }

    public String getWifiDirectName()
        {
        try {
            GetSettingCommand command = new GetSettingCommand(device, GetSettingCommand.Namespace.GLOBAL, Configuration.SETTING_WIFI_P2P_DEVICE_NAME);
            command.execute();
            return command.getResult();
            }
        catch (AdbCommunicationException e)
            {
            EventLog.ee(TAG, "getWifiDirectName() failed: %s; ignored: %s: %s", getUsbSerialNumber(), e.getMessage(), e.getCause().getMessage());
            return null;
            }
        }

    public boolean isListeningOnTcpip()
        {
        String value = getStringProperty(Configuration.PROP_ADB_TCP_PORT);
        return value != null && Integer.parseInt(value) != 0;
        }
    public boolean isListeningOnTcpip(int port)
        {
        return Integer.toString(port).equals(getStringProperty(Configuration.PROP_ADB_TCP_PORT));
        }
    public boolean listenOnTcpip()
        {
        return listenOnTcpip(Configuration.ADB_DAEMON_PORT);
        }
    public boolean listenOnTcpip(int port)
        {
        return androidDevice.getDatabase().getHostAdb().tcpip(device, port);
        }
    public boolean awaitListeningOnTcpip(long timeout, TimeUnit timeUnit)
        {
        return awaitListeningOnTcpip(Configuration.ADB_DAEMON_PORT, timeout, timeUnit);
        }
    public boolean awaitListeningOnTcpip(int port, long timeout, TimeUnit timeUnit)
        {
        long deadline = nsNow() + timeUnit.toNanos(timeout);
        while (nsNow() <= deadline)
            {
            if (isListeningOnTcpip(port))
                {
                return true;
                }
            Thread.yield();
            }
        return false;
        }
    protected long nsNow()
        {
        return System.nanoTime();
        }

    //----------------------------------------------------------------------------------------------
    // Low level accessing
    //----------------------------------------------------------------------------------------------

    /** @return null if the property doesn't exist */
    public @Nullable String getStringProperty(String property)
        {
        try
            {
            return device.getSystemProperty(property).get(Configuration.msAdbTimeoutFast, TimeUnit.MILLISECONDS);
            }
        catch (InterruptedException e)
            {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupt while retrieving property: " + property, e);
            }
        catch (ExecutionException e)
            {
            throw new RuntimeException("exception while retrieving property: " + property, e.getCause());
            }
        catch (TimeoutException e)
            {
            throw new RuntimeException("timeout while retrieving property: " + property, e);
            }
        }

    public String getStringProperty(String property, String defaultValue)
        {
        String result = getStringProperty(property);
        return result==null ? defaultValue : result;
        }
    }
