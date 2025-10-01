import { createChart, ColorType, CandlestickSeries } from 'lightweight-charts';
import React, { useEffect, useRef } from 'react';

export const ChartComponent = props => {
    const {
        data,
        colors: {
            backgroundColor = '#0f1115',
            textColor = 'black',
            upColor = '#26a69a',
            downColor = '#ef5350',
            wickUpColor = '#26a69a',
            wickDownColor = '#ef5350',
            borderUpColor = '#26a69a',
            borderDownColor = '#ef5350',
        } = {},
    } = props;

    const chartContainerRef = useRef();

    useEffect(
        () => {
            const handleResize = () => {
                chart.applyOptions({ width: chartContainerRef.current.clientWidth });
            };

            const chart = createChart(chartContainerRef.current, {
                layout: {
                    background: { type: ColorType.Solid, color: backgroundColor },
                    textColor: textColor || '#d1d5db',
                },
                width: chartContainerRef.current.clientWidth,
                height: 300,
                grid: {
                    vertLines: { color: '#2a2e39' },
                    horzLines: { color: '#2a2e39' },
                },
            });
            chart.timeScale().fitContent();

            const candleSeries = chart.addSeries(CandlestickSeries, {
                upColor,
                downColor,
                wickUpColor,
                wickDownColor,
                borderUpColor,
                borderDownColor,
            });
            candleSeries.setData(data);

            window.addEventListener('resize', handleResize);

            return () => {
                window.removeEventListener('resize', handleResize);

                chart.remove();
            };
        },
        [data, backgroundColor, textColor, upColor, downColor, wickUpColor, wickDownColor, borderUpColor, borderDownColor]
    );

    return (
        <div
            ref={chartContainerRef}
        />
    );
};

const initialData = [
    { time: '2018-12-22', open: 32.1, high: 33.0, low: 31.8, close: 32.5 },
    { time: '2018-12-23', open: 32.5, high: 32.6, low: 30.9, close: 31.1 },
    { time: '2018-12-24', open: 31.1, high: 31.2, low: 26.7, close: 27.02 },
    { time: '2018-12-25', open: 27.0, high: 27.6, low: 26.8, close: 27.32 },
    { time: '2018-12-26', open: 27.3, high: 27.4, low: 24.9, close: 25.17 },
    { time: '2018-12-27', open: 25.2, high: 29.1, low: 25.0, close: 28.89 },
    { time: '2018-12-28', open: 28.8, high: 29.0, low: 25.2, close: 25.46 },
    { time: '2018-12-29', open: 25.5, high: 25.6, low: 23.6, close: 23.92 },
    { time: '2018-12-30', open: 24.0, high: 24.2, low: 22.5, close: 22.68 },
    { time: '2018-12-31', open: 22.7, high: 22.9, low: 22.4, close: 22.67 },
];

export function Chart(props) {
    return (
        <ChartComponent {...props} data={initialData}></ChartComponent>
    );
}